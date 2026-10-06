package br.com.deladopara.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.eventing.infrastructure.ConsumerFailureMetrics;
import br.com.deladopara.payments.application.CheckoutOperationRunner;
import br.com.deladopara.payments.application.CheckoutOperations;
import br.com.deladopara.payments.application.PaymentIntentService;
import br.com.deladopara.payments.application.PaymentProvider;
import br.com.deladopara.payments.application.ProviderEventInbox;
import br.com.deladopara.payments.domain.PaymentStatus;
import br.com.deladopara.support.PostgresTestContainer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** C79: recovery gauges read the real tables and are labelled by state only. */
@SpringBootTest
@Import(PostgresTestContainer.class)
class RecoveryMetricsIT {

    private final JdbcTemplate jdbc;
    private final PaymentIntentService intents;
    private final CheckoutOperations operations;
    private final ProviderEventInbox inbox;
    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Autowired
    RecoveryMetricsIT(
            JdbcTemplate jdbc, PaymentIntentService intents, CheckoutOperations operations, ProviderEventInbox inbox) {
        this.jdbc = jdbc;
        this.intents = intents;
        this.operations = operations;
        this.inbox = inbox;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("ALTER TABLE payment_intent DISABLE TRIGGER payment_intent_reference_guard");
        jdbc.execute("TRUNCATE payment_provider_event, payment_external_operation, payment_intent,"
                + " event_consumer_failure, event_outbox CASCADE");
        jdbc.execute("ALTER TABLE payment_intent ENABLE TRIGGER payment_intent_reference_guard");
    }

    private double gauge(String name, String... tags) {
        return registry.get(name).tags(tags).gauge().value();
    }

    /** An intent whose checkout creation timed out, leaving its outcome unknown. */
    private UUID unknownIntent() {
        var intentId = intents.request(UUID.randomUUID(), 5_250, UUID.randomUUID());
        new CheckoutOperationRunner(operations, new PaymentProvider() {
                    @Override
                    public CreatedCheckout createCheckout(CheckoutRequest request) {
                        throw new IllegalStateException("timeout");
                    }

                    @Override
                    public Optional<CheckoutState> findCheckout(UUID paymentIntentId) {
                        return Optional.empty();
                    }
                })
                .runNext();
        return intentId;
    }

    @Test
    void paymentGaugesCountRecoveryWorkAndItsAge() {
        var now = Instant.parse("2026-10-05T12:00:00Z");
        new PaymentOperationalMetrics(jdbc, registry, Clock.fixed(now, ZoneOffset.UTC));
        assertThat(gauge("dlp.payments.intents", "status", "UNKNOWN")).isZero();
        assertThat(gauge("dlp.payments.intents.oldest_age_seconds", "status", "UNKNOWN"))
                .isZero();

        var first = unknownIntent();
        unknownIntent();
        jdbc.update(
                "UPDATE payment_intent SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(now.minus(Duration.ofMinutes(3))),
                first);
        var review = unknownIntent();
        intents.transition(review, PaymentStatus.UNDER_REVIEW, "UNKNOWN_UNRESOLVED", UUID.randomUUID());
        intents.request(UUID.randomUUID(), 1_000, UUID.randomUUID());
        jdbc.update("UPDATE payment_external_operation SET status = 'IN_FLIGHT', lease_until = now()"
                + " WHERE status = 'PENDING'");
        inbox.record(
                "ASAAS",
                new ProviderEventInbox.Notification("evt_1", "CHECKOUT_PAID", "chk_1", "PAID", "2026-10-05 10:00:00"));

        assertThat(gauge("dlp.payments.intents", "status", "UNKNOWN")).isEqualTo(2);
        assertThat(gauge("dlp.payments.intents", "status", "UNDER_REVIEW")).isEqualTo(1);
        assertThat(gauge("dlp.payments.intents", "status", "REFUND_REQUESTED")).isZero();
        assertThat(gauge("dlp.payments.intents.oldest_age_seconds", "status", "UNKNOWN"))
                .isEqualTo(180);
        assertThat(gauge("dlp.payments.operations.in_flight")).isEqualTo(1);
        assertThat(gauge("dlp.payments.provider_events", "status", "RECEIVED")).isEqualTo(1);
        assertThat(gauge("dlp.payments.provider_events", "status", "REVIEW")).isZero();
    }

    @Test
    void consumerGaugesSplitRetryingFromQuarantined() {
        new ConsumerFailureMetrics(jdbc, registry);
        jdbc.update("INSERT INTO event_consumer_failure (topic, partition_id, record_offset, failure_kind, state,"
                + " attempt_count, next_attempt_at, last_error, first_failed_at, updated_at)"
                + " VALUES ('t', 0, 1, 'TRANSIENT', 'RETRYING', 3, now(), 'x', now(), now()),"
                + " ('t', 0, 2, 'TRANSIENT', 'RETRYING', 2, now(), 'x', now(), now())");
        jdbc.update("INSERT INTO event_consumer_failure (topic, partition_id, record_offset, failure_kind, state,"
                + " attempt_count, last_error, first_failed_at, updated_at, quarantined_at)"
                + " VALUES ('t', 0, 3, 'INVALID', 'QUARANTINED', 1, 'x', now(), now(), now())");

        assertThat(gauge("dlp.eventing.consumer.failures", "state", "RETRYING")).isEqualTo(2);
        assertThat(gauge("dlp.eventing.consumer.failures", "state", "QUARANTINED"))
                .isEqualTo(1);
        assertThat(gauge("dlp.eventing.consumer.retrying.attempts")).isEqualTo(5);
    }
}
