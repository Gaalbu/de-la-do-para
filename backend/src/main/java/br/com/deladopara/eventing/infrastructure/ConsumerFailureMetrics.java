package br.com.deladopara.eventing.infrastructure;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumer failure gauges (C79): records waiting for a retry, records in quarantine and the attempts spent on the
 * ones still retrying. Labelled by state only.
 */
@Component
@Profile("worker")
public class ConsumerFailureMetrics {

    private final JdbcTemplate jdbc;

    public ConsumerFailureMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        for (var state : List.of("RETRYING", "QUARANTINED")) {
            Gauge.builder("dlp.eventing.consumer.failures", () -> failures(state))
                    .description("Consumed records that failed, by state")
                    .tag("state", state)
                    .register(registry);
        }
        Gauge.builder("dlp.eventing.consumer.retrying.attempts", this::retryingAttempts)
                .description("Attempts already spent on records still retrying")
                .register(registry);
    }

    private double failures(String state) {
        var value =
                jdbc.queryForObject("SELECT count(*) FROM event_consumer_failure WHERE state = ?", Long.class, state);
        return value == null ? 0 : value;
    }

    private double retryingAttempts() {
        var value = jdbc.queryForObject(
                "SELECT coalesce(sum(attempt_count), 0) FROM event_consumer_failure WHERE state = 'RETRYING'",
                Long.class);
        return value == null ? 0 : value;
    }
}
