package br.com.deladopara.eventing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.eventing.adapter.persistence.OutboxEventRepository;
import br.com.deladopara.eventing.adapter.persistence.OutboxEventWriter;
import br.com.deladopara.eventing.domain.OutboxEvent;
import br.com.deladopara.eventing.domain.OutboxEventStatus;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "logging.level.br.com.deladopara.identity.application.AdminSeeder=OFF")
@Testcontainers
@Import(PostgresTestContainer.class)
class EventingWorkerProcessRestartIT {

    private static final Duration LEASE = Duration.ofSeconds(2);
    private static final Duration POLL_DELAY = Duration.ofSeconds(1);

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    private final OutboxEventWriter writer;
    private final OutboxEventRepository events;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final PostgreSQLContainer postgres;

    @Autowired
    EventingWorkerProcessRestartIT(
            OutboxEventWriter writer,
            OutboxEventRepository events,
            TransactionTemplate transactions,
            ObjectMapper objectMapper,
            JdbcTemplate jdbc,
            PostgreSQLContainer postgres) {
        this.writer = writer;
        this.events = events;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.postgres = postgres;
    }

    @org.junit.jupiter.api.Test
    void processRestartRecoversClaimedEventAfterLeaseExpiry() throws Exception {
        var topic = createTopic();
        var event = event();
        transactions.executeWithoutResult(status -> writer.append(event));

        Process firstWorker = null;
        Process secondWorker = null;
        try {
            firstWorker = startWorker("localhost:1", topic);
            awaitAttempt(event.eventId());
            firstWorker.destroyForcibly();
            assertThat(firstWorker.waitFor(10, TimeUnit.SECONDS)).isTrue();

            Thread.sleep(LEASE.toMillis() + 250);

            secondWorker = startWorker(KAFKA.getBootstrapServers(), topic);
            awaitPublished(event.eventId());

            assertThat(events.findById(event.eventId()).orElseThrow().getStatus())
                    .isEqualTo(OutboxEventStatus.PUBLISHED);
        } finally {
            stop(secondWorker);
            stop(firstWorker);
        }
    }

    private Process startWorker(String bootstrapServers, String topic) throws Exception {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var builder = new ProcessBuilder(
                        java,
                        "-jar",
                        "target/de-la-do-para-backend-0.0.1-SNAPSHOT.jar",
                        "--spring.profiles.active=worker",
                        "--server.port=0",
                        "--logging.level.br.com.deladopara.eventing.infrastructure=DEBUG",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("APP_EVENTING_BOOTSTRAP_SERVERS", bootstrapServers);
        builder.environment().put("APP_EVENTING_TOPIC", topic);
        builder.environment().put("APP_EVENTING_LEASE", LEASE.toString());
        builder.environment().put("APP_EVENTING_BATCH_SIZE", "1");
        builder.environment().put("APP_EVENTING_POLL_DELAY", POLL_DELAY.toString());
        var process = builder.start();
        process.getOutputStream().close();
        return process;
    }

    private void awaitAttempt(UUID eventId) throws InterruptedException {
        await(() -> attemptCount(eventId) > 0);
    }

    private void awaitPublished(UUID eventId) throws InterruptedException {
        await(() -> "PUBLISHED".equals(status(eventId)));
    }

    private void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private int attemptCount(UUID eventId) {
        return jdbc.queryForObject("SELECT attempt_count FROM event_outbox WHERE event_id = ?", Integer.class, eventId);
    }

    private String status(UUID eventId) {
        return jdbc.queryForObject("SELECT status FROM event_outbox WHERE event_id = ?", String.class, eventId);
    }

    private void stop(Process process) throws InterruptedException {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    private String createTopic() throws Exception {
        var topic = "events-process-" + UUID.randomUUID().toString().substring(0, 8);
        var properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (var admin = AdminClient.create(properties)) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        }
        return topic;
    }

    private OutboxEvent event() {
        var eventId = UUID.randomUUID();
        return new OutboxEvent(
                eventId,
                "worker.process.restart",
                1,
                "aggregate-" + eventId,
                1,
                Instant.now(),
                eventId,
                eventId,
                objectMapper.createObjectNode().put("test", true));
    }
}
