package br.com.deladopara.eventing.infrastructure;

import br.com.deladopara.eventing.application.EventEnvelope;
import br.com.deladopara.eventing.domain.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class KafkaOutboxEventBroker implements OutboxEventBroker, AutoCloseable {

    private static final Duration DEFAULT_ACK_TIMEOUT = Duration.ofSeconds(20);

    private final Producer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final Duration acknowledgementTimeout;

    public KafkaOutboxEventBroker(Producer<String, String> producer, ObjectMapper objectMapper, String topic) {
        this(producer, objectMapper, topic, DEFAULT_ACK_TIMEOUT);
    }

    public KafkaOutboxEventBroker(
            Producer<String, String> producer,
            ObjectMapper objectMapper,
            String topic,
            Duration acknowledgementTimeout) {
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.topic = topic;
        if (acknowledgementTimeout.isZero() || acknowledgementTimeout.isNegative()) {
            throw new IllegalArgumentException("Kafka acknowledgement timeout must be positive");
        }
        this.acknowledgementTimeout = acknowledgementTimeout;
    }

    @Override
    public void publish(OutboxEvent event) throws OutboxPublishException {
        try {
            var value = objectMapper.writeValueAsString(EventEnvelope.from(event));
            producer.send(new ProducerRecord<>(topic, event.aggregateId(), value))
                    .get(acknowledgementTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (JsonProcessingException | InterruptedException | ExecutionException | TimeoutException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new OutboxPublishException("Kafka publish did not receive an acknowledgement", exception);
        }
    }

    @Override
    public void close() {
        producer.close();
    }
}
