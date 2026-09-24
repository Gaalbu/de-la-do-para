package br.com.deladopara.eventing.infrastructure;

import br.com.deladopara.eventing.application.EventConsumptionService;
import br.com.deladopara.eventing.application.EventEnvelopeValidator;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
        var iterator = records.iterator();
        if (!iterator.hasNext()) {
            return 0;
        }
        var record = iterator.next();
        if (!process(record)) {
            consumer.pause(
                    java.util.Set.of(new org.apache.kafka.common.TopicPartition(record.topic(), record.partition())));
            return 0;
        }
        consumer.commitSync(java.util.Map.of(
                new org.apache.kafka.common.TopicPartition(record.topic(), record.partition()),
                new org.apache.kafka.clients.consumer.OffsetAndMetadata(record.offset() + 1)));
        return 1;
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
