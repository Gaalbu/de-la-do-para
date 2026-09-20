package br.com.deladopara.infrastructure;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Prova que os gates de integração exercitam PostgreSQL e Kafka reais
 * (C09). Regras de negócio usam estes mesmos componentes a partir de C26+.
 */
@Testcontainers
class InfrastructureIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.6-bookworm");

    @Container
    static final org.testcontainers.kafka.KafkaContainer KAFKA =
            new org.testcontainers.kafka.KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    @Test
    void postgresAcceptsWrites() throws Exception {
        try (var connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE c09_probe(id serial primary key)");
            try (var results = statement.executeQuery("SELECT count(*) FROM c09_probe")) {
                Assertions.assertTrue(results.next());
                Assertions.assertEquals(0, results.getInt(1));
            }
        }
    }

    @Test
    void kafkaCreatesAndListsTopic() {
        var topic = "c09-probe-" + UUID.randomUUID().toString().substring(0, 8);
        var properties = new java.util.Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        try (var admin = AdminClient.create(properties)) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                    .all()
                    .get(60, java.util.concurrent.TimeUnit.SECONDS);
            var names = admin.listTopics().names().get(60, java.util.concurrent.TimeUnit.SECONDS);
            Assertions.assertTrue(names.contains(topic));
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }
}
