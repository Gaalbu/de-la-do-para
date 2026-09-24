package br.com.deladopara.eventing.infrastructure;

import br.com.deladopara.eventing.application.EventConsumptionService;
import br.com.deladopara.eventing.application.EventEnvelopeValidator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public KafkaEventConsumer(
            Consumer<String, String> consumer, EventEnvelopeValidator validator, EventConsumptionService consumption) {
        this.consumer = consumer;
        this.validator = validator;
        this.consumption = consumption;
    }

    public int pollAndProcess() {
        var records = consumer.poll(POLL_TIMEOUT);
        var processed = 0;
        for (var partition : records.partitions()) {
            processed += processPartition(partition, records.records(partition));
        }
        return processed;
    }

    /**
     * Processes the batch in offset order. A failure rewinds the partition to the failed record and pauses it,
     * so no later offset is committed past an unprocessed event.
     */
    private int processPartition(TopicPartition partition, List<ConsumerRecord<String, String>> records) {
        var processed = 0;
        for (var record : records) {
            if (!process(record)) {
                consumer.seek(partition, record.offset());
                consumer.pause(Set.of(partition));
                return processed;
            }
            consumer.commitSync(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)));
            processed++;
        }
        return processed;
    }

    private boolean process(ConsumerRecord<String, String> record) {
        try {
            var envelope = validator.validate(record.value());
            consumption.consume(envelope);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "event_consumer_record_uncommitted topic={} partition={} offset={} reason={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public void close() {
        consumer.close();
    }
}
