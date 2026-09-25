package br.com.deladopara.eventing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.deladopara.eventing.adapter.persistence.EventFailureRepository.Failure;
import br.com.deladopara.eventing.application.EventConsumptionOutcome;
import br.com.deladopara.eventing.application.EventConsumptionService;
import br.com.deladopara.eventing.application.EventEnvelopeValidator;
import br.com.deladopara.eventing.application.EventFailureService;
import br.com.deladopara.eventing.application.EventFailureService.FailureDecision;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class KafkaEventConsumerIT {

    private static final TopicPartition PARTITION = new TopicPartition("events", 0);
    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    private final Consumer<String, String> consumer = mockConsumer();
    private final EventEnvelopeValidator validator = mock(EventEnvelopeValidator.class);
    private final EventConsumptionService service = mock(EventConsumptionService.class);
    private final EventFailureService failures = mock(EventFailureService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @SuppressWarnings("unchecked")
    private static Consumer<String, String> mockConsumer() {
        return (Consumer<String, String>) mock(Consumer.class);
    }

    private KafkaEventConsumer adapter() {
        return new KafkaEventConsumer(consumer, validator, service, failures, clock);
    }

    private void poll(ConsumerRecord<String, String>... records) {
        when(consumer.poll(Duration.ofMillis(500)))
                .thenReturn(new ConsumerRecords<>(Map.of(PARTITION, List.of(records))));
    }

    @Test
    void commitsOffsetOnlyAfterTheTransactionalServiceReturns() {
        var envelope = mock(br.com.deladopara.eventing.application.EventEnvelope.class);
        poll(new ConsumerRecord<>("events", 0, 5L, "key", "valid"));
        when(validator.validate("valid")).thenReturn(envelope);
        when(service.consume(envelope)).thenReturn(EventConsumptionOutcome.APPLIED);

        assertThat(adapter().pollAndProcess()).isEqualTo(1);
        verify(consumer).commitSync(Map.of(PARTITION, new OffsetAndMetadata(6)));
        verify(failures, never()).recordFailure(any(), any());
    }

    @Test
    void transientFailureRewindsPausesAndDoesNotCommitUntilRetryIsDue() {
        poll(new ConsumerRecord<>("events", 0, 5L, "key", "boom"));
        var retryAt = NOW.plusSeconds(3);
        doThrow(new IllegalStateException("db down")).when(validator).validate("boom");
        when(failures.recordFailure(any(), any(RuntimeException.class)))
                .thenReturn(new FailureDecision(false, 1, retryAt));
        var adapter = adapter();

        assertThat(adapter.pollAndProcess()).isZero();
        verify(consumer, never()).commitSync(any(Map.class));
        verify(consumer).seek(PARTITION, 5L);
        verify(consumer).pause(java.util.Set.of(PARTITION));

        adapter.pollAndProcess();
        verify(consumer, never()).resume(any());
    }

    @Test
    void pausedPartitionResumesOnceTheScheduledRetryIsDue() {
        var mutable = new MutableClock(NOW);
        var adapter = new KafkaEventConsumer(consumer, validator, service, failures, mutable);
        poll(new ConsumerRecord<>("events", 0, 5L, "key", "boom"));
        doThrow(new IllegalStateException("db down")).when(validator).validate("boom");
        when(failures.recordFailure(any(), any(RuntimeException.class)))
                .thenReturn(new FailureDecision(false, 1, NOW.plusSeconds(3)));
        adapter.pollAndProcess();

        mutable.set(NOW.plusSeconds(2));
        adapter.pollAndProcess();
        verify(consumer, never()).resume(any());

        mutable.set(NOW.plusSeconds(3));
        adapter.pollAndProcess();
        verify(consumer).resume(java.util.Set.of(PARTITION));
    }

    @Test
    void quarantinedRecordAdvancesTheOffsetAndKeepsProcessingTheBatch() {
        var envelope = mock(br.com.deladopara.eventing.application.EventEnvelope.class);
        poll(
                new ConsumerRecord<>("events", 0, 5L, "a", "ok"),
                new ConsumerRecord<>("events", 0, 6L, "a", "poison"),
                new ConsumerRecord<>("events", 0, 7L, "a", "ok"));
        when(validator.validate("ok")).thenReturn(envelope);
        doThrow(new br.com.deladopara.eventing.application.InvalidEventEnvelopeException("bad"))
                .when(validator)
                .validate("poison");
        when(service.consume(envelope)).thenReturn(EventConsumptionOutcome.APPLIED);
        when(failures.recordFailure(
                        eq(new Failure("events", 0, 6L, null, null)),
                        any(br.com.deladopara.eventing.application.InvalidEventEnvelopeException.class)))
                .thenReturn(new FailureDecision(true, 1, null));

        assertThat(adapter().pollAndProcess()).isEqualTo(3);
        verify(consumer).commitSync(Map.of(PARTITION, new OffsetAndMetadata(6)));
        verify(consumer).commitSync(Map.of(PARTITION, new OffsetAndMetadata(7)));
        verify(consumer).commitSync(Map.of(PARTITION, new OffsetAndMetadata(8)));
        verify(consumer, never()).pause(any());
        verify(consumer, never()).seek(any(TopicPartition.class), any(Long.class));
    }

    @Test
    void neverCommitsPastATransientlyFailedRecordInAMultiPartitionBatch() {
        var other = new TopicPartition("events", 1);
        var envelope = mock(br.com.deladopara.eventing.application.EventEnvelope.class);
        when(consumer.poll(Duration.ofMillis(500)))
                .thenReturn(new ConsumerRecords<>(Map.of(
                        PARTITION,
                        List.of(
                                new ConsumerRecord<>("events", 0, 5L, "a", "ok"),
                                new ConsumerRecord<>("events", 0, 6L, "a", "flaky"),
                                new ConsumerRecord<>("events", 0, 7L, "a", "ok")),
                        other,
                        List.of(new ConsumerRecord<>("events", 1, 3L, "b", "ok")))));
        when(validator.validate("ok")).thenReturn(envelope);
        doThrow(new IllegalStateException("db down")).when(validator).validate("flaky");
        when(service.consume(envelope)).thenReturn(EventConsumptionOutcome.APPLIED);
        when(failures.recordFailure(any(), any(RuntimeException.class)))
                .thenReturn(new FailureDecision(false, 2, NOW.plusSeconds(1)));

        assertThat(adapter().pollAndProcess()).isEqualTo(2);
        verify(consumer).commitSync(Map.of(PARTITION, new OffsetAndMetadata(6)));
        verify(consumer, never()).commitSync(Map.of(PARTITION, new OffsetAndMetadata(7)));
        verify(consumer, never()).commitSync(Map.of(PARTITION, new OffsetAndMetadata(8)));
        verify(consumer).seek(PARTITION, 6L);
        verify(consumer).pause(java.util.Set.of(PARTITION));
        verify(consumer).commitSync(Map.of(other, new OffsetAndMetadata(4)));
        verify(service, times(2)).consume(envelope);
    }

    @Test
    void failureBookkeepingErrorKeepsTheRecordUncommittedAndPaused() {
        poll(new ConsumerRecord<>("events", 0, 5L, "key", "boom"));
        doThrow(new IllegalStateException("db down")).when(validator).validate("boom");
        when(failures.recordFailure(any(), any(RuntimeException.class)))
                .thenThrow(new IllegalStateException("bookkeeping down"));

        assertThat(adapter().pollAndProcess()).isZero();
        verify(consumer, never()).commitSync(any(Map.class));
        verify(consumer).seek(PARTITION, 5L);
        verify(consumer).pause(java.util.Set.of(PARTITION));
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
