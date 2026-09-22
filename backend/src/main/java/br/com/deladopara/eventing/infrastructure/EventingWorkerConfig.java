package br.com.deladopara.eventing.infrastructure;

import br.com.deladopara.eventing.application.OutboxEventClaimService;
import br.com.deladopara.eventing.application.OutboxPublisher;
import java.time.Clock;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@Profile("worker")
@EnableScheduling
@EnableConfigurationProperties(EventingWorkerProperties.class)
public class EventingWorkerConfig {

    @Bean(destroyMethod = "close")
    Producer<String, String> eventingKafkaProducer(EventingWorkerProperties properties) {
        var producerProperties = new Properties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProperties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        return new KafkaProducer<>(producerProperties);
    }

    @Bean(destroyMethod = "")
    KafkaOutboxEventBroker kafkaOutboxEventBroker(
            Producer<String, String> producer,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            EventingWorkerProperties properties) {
        return new KafkaOutboxEventBroker(producer, objectMapper, properties.topic());
    }

    @Bean
    OutboxPublisher outboxPublisher(
            OutboxEventClaimService claimer,
            br.com.deladopara.eventing.adapter.persistence.OutboxEventRepository events,
            KafkaOutboxEventBroker broker,
            EventingWorkerProperties properties) {
        return new OutboxPublisher(
                claimer,
                events,
                broker,
                new OutboxPublisherProperties(properties.lease(), properties.batchSize()),
                Clock.systemUTC());
    }

    @Bean
    EventingWorker eventingWorker(OutboxPublisher publisher) {
        return new EventingWorker(publisher);
    }

    static final class EventingWorker {

        private final OutboxPublisher publisher;

        EventingWorker(OutboxPublisher publisher) {
            this.publisher = publisher;
        }

        @Scheduled(fixedDelayString = "${app.eventing.publisher.poll-delay}")
        void publishPendingEvents() {
            publisher.publishBatch();
        }
    }
}
