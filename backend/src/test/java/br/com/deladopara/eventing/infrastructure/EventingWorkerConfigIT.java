package br.com.deladopara.eventing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.eventing.adapter.persistence.OutboxEventRepository;
import br.com.deladopara.eventing.adapter.persistence.OutboxEventWriter;
import br.com.deladopara.eventing.domain.OutboxEvent;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
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
@Import(PostgresTestContainer.class)
class EventingWorkerConfigIT {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    private final ApplicationContext context;
    private final OutboxEventWriter writer;
    private final OutboxEventRepository events;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    @Autowired
    EventingWorkerConfigIT(
            ApplicationContext context,
            OutboxEventWriter writer,
            OutboxEventRepository events,
            TransactionTemplate transactions,
            ObjectMapper objectMapper) {
        this.context = context;
        this.writer = writer;
        this.events = events;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
    }

    @DynamicPropertySource
    static void eventingProperties(DynamicPropertyRegistry registry) {
        registry.add("app.eventing.publisher.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.eventing.publisher.topic", () -> "events.worker-test");
        registry.add("app.eventing.publisher.lease", () -> "PT30S");
        registry.add("app.eventing.publisher.batch-size", () -> 10);
        registry.add("app.eventing.publisher.poll-delay", () -> "PT1S");
    }

    @Test
    void startsTheSeparatedWorkerWithExplicitConfiguration() {
        assertThat(context.getBean(EventingWorkerConfig.class)).isNotNull();
        assertThat(context.getBean(EventingWorkerProperties.class).lease()).isEqualTo(java.time.Duration.ofSeconds(30));
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
    }
}
