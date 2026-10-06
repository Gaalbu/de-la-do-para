package br.com.deladopara.payments.infrastructure;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Recovery gauges for the payment worker (C79): how many intents wait for a lookup, an operator or a refund, how old
 * the oldest of them is, and how many provider calls and notifications are still open. Labels are states only, never
 * order or intent ids, so cardinality stays fixed.
 */
@Component
@Profile("worker")
public class PaymentOperationalMetrics {

    /** Intent states that mean recovery work is still owed. */
    static final List<String> RECOVERY_STATES = List.of("UNKNOWN", "UNDER_REVIEW", "REFUND_REQUESTED");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public PaymentOperationalMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this(jdbc, registry, Clock.systemUTC());
    }

    PaymentOperationalMetrics(JdbcTemplate jdbc, MeterRegistry registry, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        for (var status : RECOVERY_STATES) {
            Gauge.builder("dlp.payments.intents", () -> intents(status))
                    .description("Payment intents in a state that still needs recovery work")
                    .tag("status", status)
                    .register(registry);
            Gauge.builder("dlp.payments.intents.oldest_age_seconds", () -> oldestAgeSeconds(status))
                    .description("Seconds since the oldest intent entered this state")
                    .tag("status", status)
                    .register(registry);
        }
        Gauge.builder("dlp.payments.operations.in_flight", this::operationsInFlight)
                .description("Provider calls claimed and not yet recorded")
                .register(registry);
        for (var status : List.of("RECEIVED", "REVIEW")) {
            Gauge.builder("dlp.payments.provider_events", () -> providerEvents(status))
                    .description("Provider notifications waiting for processing or for an operator")
                    .tag("status", status)
                    .register(registry);
        }
    }

    private double intents(String status) {
        return count("SELECT count(*) FROM payment_intent WHERE status = ?", status);
    }

    private double oldestAgeSeconds(String status) {
        var oldest = jdbc.queryForObject(
                "SELECT min(updated_at) FROM payment_intent WHERE status = ?", Timestamp.class, status);
        if (oldest == null) {
            return 0;
        }
        return Math.max(0, Duration.between(oldest.toInstant(), clock.instant()).toMillis() / 1000.0);
    }

    private double operationsInFlight() {
        return count("SELECT count(*) FROM payment_external_operation WHERE status = ?", "IN_FLIGHT");
    }

    private double providerEvents(String status) {
        return count("SELECT count(*) FROM payment_provider_event WHERE status = ?", status);
    }

    private double count(String sql, String status) {
        var value = jdbc.queryForObject(sql, Long.class, status);
        return value == null ? 0 : value;
    }
}
