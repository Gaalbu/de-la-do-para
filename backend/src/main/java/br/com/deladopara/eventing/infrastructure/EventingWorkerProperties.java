package br.com.deladopara.eventing.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.eventing.publisher")
public record EventingWorkerProperties(
        String bootstrapServers,
        String topic,
        Duration lease,
        int batchSize,
        Duration pollDelay,
        String consumerTopic,
        String consumerGroup) {

    public EventingWorkerProperties {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("Kafka bootstrap servers are required");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Kafka topic is required");
        }
        if (lease == null || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("Outbox lease must be positive");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("Outbox batch size must be positive");
        }
        if (pollDelay == null || pollDelay.isZero() || pollDelay.isNegative()) {
            throw new IllegalArgumentException("Outbox poll delay must be positive");
        }
        if (consumerTopic == null || consumerTopic.isBlank()) {
            throw new IllegalArgumentException("Kafka consumer topic is required");
        }
        if (consumerGroup == null || consumerGroup.isBlank()) {
            throw new IllegalArgumentException("Kafka consumer group is required");
        }
    }
}
