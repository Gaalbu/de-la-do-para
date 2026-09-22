package br.com.deladopara.eventing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.support.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

    @Autowired
    EventingWorkerConfigIT(ApplicationContext context) {
        this.context = context;
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
}
