package br.com.deladopara.eventing.infrastructure;

import br.com.deladopara.eventing.application.EventEnvelope;
import br.com.deladopara.eventing.domain.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class KafkaOutboxEventBroker implements OutboxEventBroker, AutoCloseable {

    private final Producer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaOutboxEventBroker(Producer<String, String> producer, ObjectMapper objectMapper, String topic) {
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(OutboxEvent event) throws OutboxPublishException {
        try {
            var value = objectMapper.writeValueAsString(EventEnvelope.from(event));
            producer.send(new ProducerRecord<>(topic, event.aggregateId(), value))
                    .get();
        } catch (JsonProcessingException | InterruptedException | ExecutionException exception) {
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
