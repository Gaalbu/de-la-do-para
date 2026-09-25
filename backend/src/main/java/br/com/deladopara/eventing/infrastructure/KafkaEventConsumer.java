package br.com.deladopara.eventing.infrastructure;

import br.com.deladopara.eventing.adapter.persistence.EventFailureRepository.Failure;
import br.com.deladopara.eventing.application.EventConsumptionService;
import br.com.deladopara.eventing.application.EventEnvelope;
import br.com.deladopara.eventing.application.EventEnvelopeValidator;
import br.com.deladopara.eventing.application.EventFailureService;
import br.com.deladopara.eventing.application.EventFailureService.FailureDecision;
import br.com.deladopara.eventing.application.EventRetryPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaEventConsumer implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaEventConsumer.class);
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);

    private final Consumer<String, String> consumer;
    private final EventEnvelopeValidator validator;
    private final EventConsumptionService consumption;
    private final EventFailureService failures;
    private final Clock clock;
    private final Map<TopicPartition, Instant> pausedUntil = new HashMap<>();

    public KafkaEventConsumer(
            Consumer<String, String> consumer,
            EventEnvelopeValidator validator,
            EventConsumptionService consumption,
            EventFailureService failures,
            Clock clock) {
        this.consumer = consumer;
        this.validator = validator;
        this.consumption = consumption;
        this.failures = failures;
        this.clock = clock;
    }

    public int pollAndProcess() {
        resumeDuePartitions();
        var records = consumer.poll(POLL_TIMEOUT);
        var processed = 0;
        for (var partition : records.partitions()) {
            processed += processPartition(partition, records.records(partition));
        }
        return processed;
    }

    private void resumeDuePartitions() {
        var now = clock.instant();
        var due = pausedUntil.entrySet().stream()
                .filter(entry -> !entry.getValue().isAfter(now))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (!due.isEmpty()) {
            consumer.resume(due);
            due.forEach(pausedUntil::remove);
        }
    }

    /**
     * Processes the batch in offset order. A transient failure rewinds the partition to the failed record and pauses
     * it until the retry is due; an invalid or exhausted record is quarantined durably and skipped, so a poison
     * message never blocks the partition indefinitely and no offset is committed past an unprocessed event.
     */
    private int processPartition(TopicPartition partition, List<ConsumerRecord<String, String>> records) {
        var processed = 0;
        for (var record : records) {
            if (!processOrHandleFailure(partition, record)) {
                return processed;
            }
            consumer.commitSync(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)));
            processed++;
        }
        return processed;
    }

    /** Returns true when the offset may advance past the record (applied, duplicate or quarantined). */
    private boolean processOrHandleFailure(TopicPartition partition, ConsumerRecord<String, String> record) {
        EventEnvelope envelope = null;
        try {
            envelope = validator.validate(record.value());
            consumption.consume(envelope);
            return true;
        } catch (RuntimeException exception) {
            return handleFailure(partition, record, envelope, exception);
        }
    }

    private boolean handleFailure(
            TopicPartition partition,
            ConsumerRecord<String, String> record,
            EventEnvelope envelope,
            RuntimeException exception) {
        var failure = new Failure(
                record.topic(),
                record.partition(),
                record.offset(),
                envelope == null ? null : envelope.eventId(),
                envelope == null ? null : envelope.correlationId());
        FailureDecision decision;
        try {
            decision = failures.recordFailure(failure, exception);
        } catch (RuntimeException bookkeeping) {
            LOGGER.warn(
                    "event_consumer_failure_unrecorded topic={} partition={} offset={} reason={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    bookkeeping.getClass().getSimpleName());
            pauseFor(partition, record, clock.instant().plus(EventRetryPolicy.MAX_DELAY));
            return false;
        }
        if (decision.quarantined()) {
            LOGGER.warn(
                    "event_consumer_record_quarantined topic={} partition={} offset={} attempts={} reason={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    decision.attempts(),
                    exception.getClass().getSimpleName());
            return true;
        }
        LOGGER.warn(
                "event_consumer_record_retry_scheduled topic={} partition={} offset={} attempts={} reason={}",
                record.topic(),
                record.partition(),
                record.offset(),
                decision.attempts(),
                exception.getClass().getSimpleName());
        pauseFor(partition, record, decision.nextAttemptAt());
        return false;
    }

    private void pauseFor(TopicPartition partition, ConsumerRecord<String, String> record, Instant resumeAt) {
        consumer.seek(partition, record.offset());
        consumer.pause(Set.of(partition));
        pausedUntil.put(partition, resumeAt);
    }

    @Override
    public void close() {
        consumer.close();
    }
}
