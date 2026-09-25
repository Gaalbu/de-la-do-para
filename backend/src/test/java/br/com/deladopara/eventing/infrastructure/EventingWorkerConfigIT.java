package br.com.deladopara.eventing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.eventing.adapter.persistence.EventConsumptionRepository;
import br.com.deladopara.eventing.adapter.persistence.OutboxEventRepository;
import br.com.deladopara.eventing.adapter.persistence.OutboxEventWriter;
import br.com.deladopara.eventing.application.EventConsumptionOutcome;
import br.com.deladopara.eventing.application.EventConsumptionService;
import br.com.deladopara.eventing.application.EventEnvelope;
import br.com.deladopara.eventing.application.EventEnvelopeValidator;
import br.com.deladopara.eventing.application.EventFailureService;
import br.com.deladopara.eventing.application.EventHandler;
import br.com.deladopara.eventing.domain.OutboxEvent;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("worker")
@Testcontainers
@Import({PostgresTestContainer.class, EventingWorkerConfigIT.ConsumerTestConfiguration.class})
class EventingWorkerConfigIT {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    private final ApplicationContext context;
    private final OutboxEventWriter writer;
    private final OutboxEventRepository events;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final MeterRegistry metrics;
    private final JdbcTemplate jdbc;
    private final EventConsumptionRepository consumptions;
    private final EventConsumptionService consumptionService;
    private final EventEnvelopeValidator envelopeValidator;
    private final EventFailureService failures;
    private final TestOrderHandler handler;

    @Autowired
    EventingWorkerConfigIT(
            ApplicationContext context,
            OutboxEventWriter writer,
            OutboxEventRepository events,
            TransactionTemplate transactions,
            ObjectMapper objectMapper,
            MeterRegistry metrics,
            JdbcTemplate jdbc,
            EventConsumptionRepository consumptions,
            EventConsumptionService consumptionService,
            EventEnvelopeValidator envelopeValidator,
            EventFailureService failures,
            TestOrderHandler handler) {
        this.context = context;
        this.writer = writer;
        this.events = events;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.jdbc = jdbc;
        this.consumptions = consumptions;
        this.consumptionService = consumptionService;
        this.envelopeValidator = envelopeValidator;
        this.failures = failures;
        this.handler = handler;
    }

    @DynamicPropertySource
    static void eventingProperties(DynamicPropertyRegistry registry) {
        registry.add("app.eventing.publisher.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.eventing.publisher.topic", () -> "events.worker-test");
        registry.add("app.eventing.publisher.lease", () -> "PT30S");
        registry.add("app.eventing.publisher.batch-size", () -> 10);
        registry.add("app.eventing.publisher.poll-delay", () -> "PT1S");
        registry.add("app.eventing.consumer.enabled", () -> true);
        registry.add("app.eventing.consumer.poll-delay", () -> "PT0.1S");
        registry.add("app.eventing.publisher.consumer-topic", () -> "events.worker-test.inbound");
        registry.add("app.eventing.publisher.consumer-group", () -> "worker-test");
    }

    @Test
    void startsTheSeparatedWorkerWithExplicitConfiguration() {
        assertThat(context.getBean(EventingWorkerConfig.class)).isNotNull();
        assertThat(context.getBean(EventingWorkerProperties.class).lease()).isEqualTo(java.time.Duration.ofSeconds(30));
        assertThat(context.getBean(KafkaEventConsumer.class)).isNotNull();
        assertThat(context.getBean(EventingWorkerProperties.class).topic())
                .isNotEqualTo(context.getBean(EventingWorkerProperties.class).consumerTopic());
    }

    @Test
    void configuredWorkerPublishesPendingEvent() {
        var eventId = UUID.randomUUID();
        var event = new OutboxEvent(
                eventId,
                "worker.test",
                1,
                "worker-aggregate-" + eventId,
                0,
                Instant.now(),
                eventId,
                eventId,
                objectMapper.createObjectNode().put("test", true));
        transactions.executeWithoutResult(status -> writer.append(event));

        context.getBean(EventingWorkerConfig.EventingWorker.class).publishPendingEvents();

        var persisted = events.findById(eventId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(br.com.deladopara.eventing.domain.OutboxEventStatus.PUBLISHED);
        assertThat(persisted.getAttemptCount()).isEqualTo(1);
        assertThat(metrics.get("dlp.eventing.outbox.pending.count").gauge().value())
                .isZero();
    }

    @Test
    void realKafkaDeliveryCommitsAfterPostgresEffectAndReceiptAndReplayIsIdempotent() throws Exception {
        var topic = "events.consumer-it-" + UUID.randomUUID();
        var group = "consumer-it-" + UUID.randomUUID();
        createTopic(topic);
        jdbc.execute("CREATE TABLE IF NOT EXISTS event_handler_effect (event_id UUID PRIMARY KEY)");
        jdbc.execute("TRUNCATE event_handler_effect, event_consumption, event_consumer_cursor");
        var eventId = UUID.randomUUID();
        var event = new EventEnvelope(
                eventId,
                "order.created",
                1,
                "kafka-order-" + eventId,
                0,
                Instant.now().toString(),
                eventId,
                eventId,
                objectMapper.createObjectNode().put("orderId", "order-" + eventId));
        var serialized = objectMapper.writeValueAsString(event);
        try (var producer = createProducer();
                var firstConsumer = createConsumer(group)) {
            producer.send(new ProducerRecord<>(topic, event.aggregateId(), serialized))
                    .get(10, TimeUnit.SECONDS);
            firstConsumer.subscribe(List.of(topic));
            var firstDelivery = awaitRecord(firstConsumer);
            assertThat(consumptionService.consume(envelopeValidator.validate(firstDelivery.value())))
                    .isEqualTo(EventConsumptionOutcome.APPLIED);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM event_handler_effect", Long.class))
                    .isEqualTo(1L);
            assertThat(consumptions.find(handler.handlerName(), eventId))
                    .get()
                    .extracting(receipt -> receipt.result().name())
                    .isEqualTo("APPLIED");
        }

        try (var restartedConsumer = createConsumer(group)) {
            restartedConsumer.subscribe(List.of(topic));
            var adapter = new KafkaEventConsumer(
                    restartedConsumer, envelopeValidator, consumptionService, failures, Clock.systemUTC());
            assertThat(awaitConsumerCommit(adapter)).isEqualTo(1);
            var partition = new org.apache.kafka.common.TopicPartition(topic, 0);
            var committed =
                    restartedConsumer.committed(java.util.Set.of(partition)).get(partition);
            assertThat(committed).isNotNull();
            assertThat(committed.offset()).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM event_handler_effect", Long.class))
                    .isEqualTo(1L);
        }

        try (var consumerAfterCommittedRestart = createConsumer(group)) {
            consumerAfterCommittedRestart.subscribe(List.of(topic));
            var committedOffset = new org.apache.kafka.common.TopicPartition(topic, 0);
            var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            var unexpectedRecord = false;
            while (System.nanoTime() < deadline) {
                if (!consumerAfterCommittedRestart.poll(Duration.ofMillis(250)).isEmpty()) {
                    unexpectedRecord = true;
                    break;
                }
            }

            assertThat(unexpectedRecord).isFalse();
            assertThat(consumerAfterCommittedRestart
                            .committed(java.util.Set.of(committedOffset))
                            .get(committedOffset)
                            .offset())
                    .isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM event_handler_effect", Long.class))
                    .isEqualTo(1L);
        }
    }

    @Test
    void kafkaRebalanceRedeliversAppliedEventAndConsumerCommitsAfterDedupe() throws Exception {
        var topic = "events.rebalance-it-" + UUID.randomUUID();
        var group = "rebalance-it-" + UUID.randomUUID();
        createTopic(topic);
        jdbc.execute("CREATE TABLE IF NOT EXISTS event_handler_effect (event_id UUID PRIMARY KEY)");
        jdbc.execute("TRUNCATE event_handler_effect, event_consumption, event_consumer_cursor");
        var eventId = UUID.randomUUID();
        var event = new EventEnvelope(
                eventId,
                "order.created",
                1,
                "rebalance-order-" + eventId,
                0,
                Instant.now().toString(),
                eventId,
                eventId,
                objectMapper.createObjectNode().put("orderId", "rebalance-order-" + eventId));
        try (var producer = createProducer();
                var firstConsumer = createConsumer(group)) {
            producer.send(new ProducerRecord<>(topic, event.aggregateId(), objectMapper.writeValueAsString(event)))
                    .get(10, TimeUnit.SECONDS);
            firstConsumer.subscribe(List.of(topic));
            var firstDelivery = awaitRecord(firstConsumer);
            assertThat(consumptionService.consume(envelopeValidator.validate(firstDelivery.value())))
                    .isEqualTo(EventConsumptionOutcome.APPLIED);
            var partition = new org.apache.kafka.common.TopicPartition(topic, 0);
            assertThat(firstConsumer.committed(java.util.Set.of(partition)).get(partition))
                    .isNull();

            var secondConsumer = createConsumer(group);
            try (secondConsumer) {
                secondConsumer.subscribe(List.of(topic));
                secondConsumer.poll(Duration.ofMillis(100));
                firstConsumer.poll(Duration.ofMillis(100));
                firstConsumer.close();
                var secondAdapter = new KafkaEventConsumer(
                        secondConsumer, envelopeValidator, consumptionService, failures, Clock.systemUTC());
                var deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                var committedAfterRebalance = false;
                while (System.nanoTime() < deadline && !committedAfterRebalance) {
                    committedAfterRebalance = secondAdapter.pollAndProcess() == 1;
                }

                assertThat(committedAfterRebalance).isTrue();
                assertThat(secondConsumer
                                .committed(java.util.Set.of(partition))
                                .get(partition)
                                .offset())
                        .isEqualTo(1L);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM event_handler_effect", Long.class))
                        .isEqualTo(1L);
                assertThat(consumptions.find(handler.handlerName(), eventId))
                        .get()
                        .extracting(receipt -> receipt.result().name())
                        .isEqualTo("APPLIED");
            }
        }
    }

    @Test
    void incompatibleSchemaIsQuarantinedDurablyAndDoesNotBlockLaterEvents() throws Exception {
        var topic = "events.quarantine-it-" + UUID.randomUUID();
        var group = "quarantine-it-" + UUID.randomUUID();
        createTopic(topic);
        jdbc.execute("CREATE TABLE IF NOT EXISTS event_handler_effect (event_id UUID PRIMARY KEY)");
        jdbc.execute("TRUNCATE event_handler_effect, event_consumption, event_consumer_cursor");
        var poisonId = UUID.randomUUID();
        var poison = new EventEnvelope(
                poisonId,
                "order.created",
                2,
                "poison-order-" + poisonId,
                0,
                Instant.now().toString(),
                poisonId,
                poisonId,
                objectMapper.createObjectNode().put("orderId", "secret-" + poisonId));
        var validId = UUID.randomUUID();
        var valid = new EventEnvelope(
                validId,
                "order.created",
                1,
                "valid-order-" + validId,
                0,
                Instant.now().toString(),
                validId,
                validId,
                objectMapper.createObjectNode().put("orderId", "order-" + validId));
        try (var producer = createProducer();
                var consumer = createConsumer(group)) {
            producer.send(new ProducerRecord<>(topic, poison.aggregateId(), objectMapper.writeValueAsString(poison)))
                    .get(10, TimeUnit.SECONDS);
            producer.send(new ProducerRecord<>(topic, poison.aggregateId(), objectMapper.writeValueAsString(valid)))
                    .get(10, TimeUnit.SECONDS);
            consumer.subscribe(List.of(topic));
            var adapter = new KafkaEventConsumer(
                    consumer, envelopeValidator, consumptionService, failures, Clock.systemUTC());
            var partition = new org.apache.kafka.common.TopicPartition(topic, 0);
            var deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (System.nanoTime() < deadline
                    && consumptions.find(handler.handlerName(), validId).isEmpty()) {
                adapter.pollAndProcess();
            }

            assertThat(consumer.paused()).doesNotContain(partition);
            assertThat(consumer.committed(java.util.Set.of(partition))
                            .get(partition)
                            .offset())
                    .isEqualTo(2L);
            assertThat(consumptions.find(handler.handlerName(), poisonId)).isEmpty();
            assertThat(consumptions.find(handler.handlerName(), validId)).isPresent();
            var row = jdbc.queryForMap(
                    "SELECT state, failure_kind, attempt_count, last_error, event_id, correlation_id"
                            + " FROM event_consumer_failure WHERE topic = ? AND record_offset = 0",
                    topic);
            assertThat(row.get("state")).isEqualTo("QUARANTINED");
            assertThat(row.get("failure_kind")).isEqualTo("INVALID");
            assertThat(row.get("attempt_count")).isEqualTo(1);
            assertThat(row.get("last_error")).isEqualTo("INVALID:UnsupportedEventException");
            assertThat(row.get("last_error").toString()).doesNotContain("secret");
            assertThat(row.get("event_id").toString()).isEqualTo(poisonId.toString());
            assertThat(row.get("correlation_id").toString()).isEqualTo(poisonId.toString());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM event_handler_effect", Long.class))
                    .isEqualTo(1L);
        }
    }

    private void createTopic(String topic) throws Exception {
        var properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (var admin = AdminClient.create(properties)) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get(10, TimeUnit.SECONDS);
        }
    }

    private KafkaProducer<String, String> createProducer() {
        var properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(properties);
    }

    private KafkaConsumer<String, String> createConsumer(String group) {
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(properties);
    }

    private org.apache.kafka.clients.consumer.ConsumerRecord<String, String> awaitRecord(
            KafkaConsumer<String, String> consumer) {
        var deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(250));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new AssertionError("Kafka consumer did not receive the test event");
    }

    private int awaitConsumerCommit(KafkaEventConsumer adapter) {
        var deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (adapter.pollAndProcess() == 1) {
                return 1;
            }
        }
        throw new AssertionError("Kafka consumer did not process the uncommitted event after restart");
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ConsumerTestConfiguration {

        @org.springframework.context.annotation.Bean
        TestOrderHandler testOrderHandler(JdbcTemplate jdbc) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS event_handler_effect (event_id UUID PRIMARY KEY)");
            return new TestOrderHandler(jdbc);
        }
    }

    static class TestOrderHandler implements EventHandler {

        private final JdbcTemplate jdbc;

        TestOrderHandler(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public String handlerName() {
            return "kafka-worker-it-order-handler";
        }

        @Override
        public String eventType() {
            return "order.created";
        }

        @Override
        public int schemaVersion() {
            return 1;
        }

        @Override
        public void handle(EventEnvelope event) {
            jdbc.update("INSERT INTO event_handler_effect (event_id) VALUES (?)", event.eventId());
        }
    }
}
