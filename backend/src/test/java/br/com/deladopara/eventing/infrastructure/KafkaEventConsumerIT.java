package br.com.deladopara.eventing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.deladopara.eventing.application.EventConsumptionOutcome;
import br.com.deladopara.eventing.application.EventConsumptionService;
import br.com.deladopara.eventing.application.EventEnvelopeValidator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class KafkaEventConsumerIT {

    @Test
    void commitsOffsetOnlyAfterTheTransactionalServiceReturns() {
        @SuppressWarnings("unchecked")
        var consumer = (Consumer<String, String>) mock(Consumer.class);
        var validator = mock(EventEnvelopeValidator.class);
        var service = mock(EventConsumptionService.class);
        var envelope = org.mockito.Mockito.mock(br.com.deladopara.eventing.application.EventEnvelope.class);
        var record = new ConsumerRecord<>("events", 0, 5L, "key", "valid");
        when(consumer.poll(Duration.ofMillis(500)))
                .thenReturn(new ConsumerRecords<>(Map.of(new TopicPartition("events", 0), List.of(record))));
        when(validator.validate("valid")).thenReturn(envelope);
        when(service.consume(envelope)).thenReturn(EventConsumptionOutcome.APPLIED);
        var adapter = new KafkaEventConsumer(consumer, validator, service);

        assertThat(adapter.pollAndProcess()).isEqualTo(1);
        verify(consumer).commitSync(Map.of(new TopicPartition("events", 0), new OffsetAndMetadata(6)));
    }

    @Test
    void leavesOffsetUncommittedOnInvalidEnvelopeOrHandlerFailure() {
        @SuppressWarnings("unchecked")
        var consumer = (Consumer<String, String>) mock(Consumer.class);
        var validator = mock(EventEnvelopeValidator.class);
        var service = mock(EventConsumptionService.class);
        var record = new ConsumerRecord<>("events", 0, 5L, "key", "invalid");
        when(consumer.poll(Duration.ofMillis(500)))
                .thenReturn(new ConsumerRecords<>(Map.of(new TopicPartition("events", 0), List.of(record))));
        doThrow(new IllegalStateException("invalid schema")).when(validator).validate("invalid");
        var adapter = new KafkaEventConsumer(consumer, validator, service);

        assertThat(adapter.pollAndProcess()).isZero();
        verify(consumer, never()).commitSync(any(Map.class));
        verify(consumer).seek(new TopicPartition("events", 0), 5L);
        verify(consumer, times(1)).pause(java.util.Set.of(new TopicPartition("events", 0)));
        verify(service, never()).consume(any());
    }

    @Test
    void processesWholeBatchAndNeverCommitsPastAFailedRecord() {
        @SuppressWarnings("unchecked")
        var consumer = (Consumer<String, String>) mock(Consumer.class);
        var validator = mock(EventEnvelopeValidator.class);
        var service = mock(EventConsumptionService.class);
        var partition = new TopicPartition("events", 0);
        var otherPartition = new TopicPartition("events", 1);
        var envelope = mock(br.com.deladopara.eventing.application.EventEnvelope.class);
        when(consumer.poll(Duration.ofMillis(500)))
                .thenReturn(new ConsumerRecords<>(Map.of(
                        partition,
                        List.of(
                                new ConsumerRecord<>("events", 0, 5L, "a", "ok"),
                                new ConsumerRecord<>("events", 0, 6L, "a", "ok"),
                                new ConsumerRecord<>("events", 0, 7L, "a", "invalid"),
                                new ConsumerRecord<>("events", 0, 8L, "a", "ok")),
                        otherPartition,
                        List.of(new ConsumerRecord<>("events", 1, 3L, "b", "ok")))));
        when(validator.validate("ok")).thenReturn(envelope);
        doThrow(new IllegalStateException("invalid schema")).when(validator).validate("invalid");
        when(service.consume(envelope)).thenReturn(EventConsumptionOutcome.APPLIED);
        var adapter = new KafkaEventConsumer(consumer, validator, service);

        assertThat(adapter.pollAndProcess()).isEqualTo(3);
        verify(consumer).commitSync(Map.of(partition, new OffsetAndMetadata(6)));
        verify(consumer).commitSync(Map.of(partition, new OffsetAndMetadata(7)));
        verify(consumer, never()).commitSync(Map.of(partition, new OffsetAndMetadata(8)));
        verify(consumer, never()).commitSync(Map.of(partition, new OffsetAndMetadata(9)));
        verify(consumer).seek(partition, 7L);
        verify(consumer).pause(java.util.Set.of(partition));
        verify(consumer).commitSync(Map.of(otherPartition, new OffsetAndMetadata(4)));
        verify(service, times(3)).consume(envelope);
    }
}
