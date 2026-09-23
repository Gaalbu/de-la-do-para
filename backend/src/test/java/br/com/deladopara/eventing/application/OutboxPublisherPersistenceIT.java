package br.com.deladopara.eventing.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.eventing.adapter.persistence.OutboxEventRepository;
import br.com.deladopara.eventing.adapter.persistence.OutboxEventWriter;
import br.com.deladopara.eventing.domain.OutboxEvent;
import br.com.deladopara.eventing.domain.OutboxEventStatus;
import br.com.deladopara.eventing.infrastructure.KafkaOutboxEventBroker;
import br.com.deladopara.eventing.infrastructure.OutboxEventBroker;
import br.com.deladopara.eventing.infrastructure.OutboxPublishException;
import br.com.deladopara.eventing.infrastructure.OutboxPublisherProperties;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@Import(PostgresTestContainer.class)
class OutboxPublisherPersistenceIT {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    private final OutboxEventWriter writer;
    private final OutboxEventClaimService claimer;
    private final OutboxEventRepository events;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    @Autowired
    OutboxPublisherPersistenceIT(
            OutboxEventWriter writer,
            OutboxEventClaimService claimer,
            OutboxEventRepository events,
            TransactionTemplate transactions,
            ObjectMapper objectMapper) {
        this.writer = writer;
        this.claimer = claimer;
        this.events = events;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
    }

    private static String topic;

    @BeforeAll
    static void createTopic() throws Exception {
        topic = createTopicForTest();
    }

    private static String createTopicForTest() throws Exception {
        var topicName = "events-" + UUID.randomUUID().toString().substring(0, 8);
        var properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (var admin = AdminClient.create(properties)) {
            admin.createTopics(List.of(new NewTopic(topicName, 1, (short) 1)))
                    .all()
                    .get();
        }
        return topicName;
    }

    @BeforeEach
    void clearOutbox() {
        transactions.executeWithoutResult(status -> events.deleteAllInBatch());
    }

    @Test
    void claimsPublishesAndMarksOnlyAfterKafkaAcknowledgement() throws Exception {
        var event = event();
        transactions.executeWithoutResult(status -> writer.append(event));

        try (var producer = new KafkaProducer<String, String>(producerProperties());
                var broker = new KafkaOutboxEventBroker(producer, objectMapper, topic)) {
            var publisher = new OutboxPublisher(
                    claimer,
                    events,
                    broker,
                    new OutboxPublisherProperties(Duration.ofSeconds(30), 10),
                    Clock.fixed(NOW, ZoneOffset.UTC));

            assertThat(publisher.publishBatch()).isEqualTo(1);
        }

        var persisted = events.findById(event.eventId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(persisted.getAttemptCount()).isEqualTo(1);
        assertThat(readOne()).satisfies(record -> {
            assertThat(record.key()).isEqualTo(event.aggregateId());
            assertThat(record.value().get("eventId").asText())
                    .isEqualTo(event.eventId().toString());
            assertThat(record.value().get("payload").get("orderId").asText()).isEqualTo("ord_01JABCDEF");
            assertThat(record.value().has("attemptCount")).isFalse();
        });
    }

    @Test
    void expiredLeaseMakesTheEventClaimableAgain() {
        var event = event();
        transactions.executeWithoutResult(status -> writer.append(event));

        var first = claimer.claim(NOW, Duration.ofSeconds(30), 10);
        var second = claimer.claim(NOW.plusSeconds(31), Duration.ofSeconds(30), 10);

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.getFirst().event().eventId()).isEqualTo(event.eventId());
        assertThat(events.findById(event.eventId()).orElseThrow().getAttemptCount())
                .isEqualTo(2);
    }

    @Test
    void redeliversAfterWorkerDiesAfterKafkaAckBeforeDatabaseMark() throws Exception {
        var event = event();
        var redeliveryTopic = createTopicForTest();
        transactions.executeWithoutResult(status -> writer.append(event));

        try (var producer = new KafkaProducer<String, String>(producerProperties());
                var broker = new KafkaOutboxEventBroker(producer, objectMapper, redeliveryTopic)) {
            var crashedPublisher = new OutboxPublisher(
                    claimer,
                    events,
                    new AckThenCrashBroker(broker),
                    new OutboxPublisherProperties(Duration.ofSeconds(30), 10),
                    Clock.fixed(NOW, ZoneOffset.UTC));

            assertThat(crashedPublisher.publishBatch()).isZero();
            assertThat(events.findById(event.eventId()).orElseThrow().getStatus())
                    .isEqualTo(OutboxEventStatus.PENDING);

            var restartedPublisher = new OutboxPublisher(
                    claimer,
                    events,
                    broker,
                    new OutboxPublisherProperties(Duration.ofSeconds(30), 10),
                    Clock.fixed(NOW.plusSeconds(31), ZoneOffset.UTC));

            assertThat(restartedPublisher.publishBatch()).isEqualTo(1);
        }

        assertThat(events.findById(event.eventId()).orElseThrow().getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(readAll(redeliveryTopic))
                .hasSize(2)
                .allSatisfy(record -> assertThat(record.value().get("eventId").asText())
                        .isEqualTo(event.eventId().toString()));
    }

    private List<PublishedRecord> readAll() {
        return readAll(topic);
    }

    private List<PublishedRecord> readAll(String topicName) {
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.assign(List.of(new TopicPartition(topicName, 0)));
            var records = consumer.poll(Duration.ofSeconds(10));
            return records.records(new TopicPartition(topicName, 0)).stream()
                    .map(record -> {
                        try {
                            return new PublishedRecord(record.key(), objectMapper.readTree(record.value()));
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .toList();
        }
    }

    private PublishedRecord readOne() {
        var records = readAll();
        assertThat(records).hasSize(1);
        return records.getFirst();
    }

    private static final class AckThenCrashBroker implements OutboxEventBroker {

        private final OutboxEventBroker delegate;

        private AckThenCrashBroker(OutboxEventBroker delegate) {
            this.delegate = delegate;
        }

        @Override
        public void publish(OutboxEvent event) {
            delegate.publish(event);
            throw new OutboxPublishException("worker stopped after Kafka acknowledgement", new RuntimeException());
        }
    }

    private record PublishedRecord(String key, JsonNode value) {}

    private Properties producerProperties() {
        var properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        return properties;
    }

    private OutboxEvent event() {
        var id = UUID.randomUUID();
        return new OutboxEvent(
                id,
                "order.created",
                1,
                "ord_01JABCDEF-" + id,
                0,
                NOW,
                id,
                id,
                objectMapper.createObjectNode().put("orderId", "ord_01JABCDEF"));
    }
}
