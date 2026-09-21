package br.com.deladopara.observability;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.support.PostgresTestContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestContainer.class)
class HealthEndpointTest {
    private final int port;
    private final ConfigurableApplicationContext context;

    @Autowired
    HealthEndpointTest(@Value("${local.server.port}") int port, ConfigurableApplicationContext context) {
        this.port = port;
        this.context = context;
    }

    @Test
    void healthAndProbesExposeOnlyStatusWithCorrelation() throws Exception {
        for (var path : new String[] {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            var response = get(path);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("components", "details");
            assertThat(UUID.fromString(
                            response.headers().firstValue("X-Request-ID").orElseThrow()))
                    .isNotNull();
        }
    }

    @Test
    void configurationAndMetricsAreNotPubliclyExposed() throws Exception {
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
        assertThat(get("/actuator/metrics").statusCode()).isEqualTo(404);
    }

    @Test
    void readinessRefusesTrafficWithoutFailingLiveness() throws Exception {
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
        try {
            var response = get("/actuator/health/readiness");
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.body()).isEqualTo("{\"status\":\"OUT_OF_SERVICE\"}");
            assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        } finally {
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
