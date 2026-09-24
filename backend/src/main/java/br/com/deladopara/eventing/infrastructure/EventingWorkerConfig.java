package br.com.deladopara.eventing.infrastructure;

import br.com.deladopara.eventing.application.OutboxEventClaimService;
import br.com.deladopara.eventing.application.OutboxPublisher;
import java.time.Clock;
import java.util.Properties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.eventing.consumer", name = "enabled", havingValue = "true")
    Consumer<String, String> eventingKafkaConsumer(EventingWorkerProperties properties) {
        var consumerProperties = new Properties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, properties.consumerGroup());
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(consumerProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.eventing.consumer", name = "enabled", havingValue = "true")
    KafkaEventConsumer kafkaEventConsumer(
            Consumer<String, String> consumer,
            br.com.deladopara.eventing.application.EventEnvelopeValidator validator,
            br.com.deladopara.eventing.application.EventConsumptionService consumption,
            EventingWorkerProperties properties) {
        consumer.subscribe(java.util.List.of(properties.consumerTopic()));
        return new KafkaEventConsumer(consumer, validator, consumption);
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
    EventingWorker eventingWorker(
            OutboxPublisher publisher, org.springframework.beans.factory.ObjectProvider<KafkaEventConsumer> consumer) {
        return new EventingWorker(publisher, consumer);
    }

    static class EventingWorker {

        private final OutboxPublisher publisher;
        private final KafkaEventConsumer consumer;

        EventingWorker(
                OutboxPublisher publisher,
                org.springframework.beans.factory.ObjectProvider<KafkaEventConsumer> consumer) {
            this.publisher = publisher;
            this.consumer = consumer.getIfAvailable();
        }

        @Scheduled(fixedDelayString = "${app.eventing.publisher.poll-delay}")
        void publishPendingEvents() {
            publisher.publishBatch();
        }

        @Scheduled(fixedDelayString = "${app.eventing.consumer.poll-delay:PT0.5S}")
        void consumeEvents() {
            if (consumer != null) {
                consumer.pollAndProcess();
            }
        }
    }
}
