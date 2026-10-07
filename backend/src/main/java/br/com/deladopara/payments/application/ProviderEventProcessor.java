package br.com.deladopara.payments.application;

import br.com.deladopara.payments.application.PaymentProvider.CheckoutState;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutStatus;
import br.com.deladopara.payments.domain.PaymentStatus;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns stored provider notifications into payment facts. A notification is never trusted on its own: the provider
 * is queried outside any transaction, and only a PAID state of the same checkout for the exact amount confirms the
 * payment (V09). A lookup that fails or does not show the payment yet proves nothing, so the notification is retried
 * with backoff and, after {@link #MAX_ATTEMPTS}, kept as REVIEW for the operator. Notifications for checkouts not yet
 * linked to an intent stay RECEIVED for reconciliation.
 */
public class ProviderEventProcessor {

    /** SPEC-eventing values (8 attempts, 1 s doubling up to 1 min). */
    static final int MAX_ATTEMPTS = 8;

    static final Duration BASE_DELAY = Duration.ofSeconds(1);

    static final Duration MAX_DELAY = Duration.ofMinutes(1);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PaymentIntentService intents;
    private final PaymentProvider provider;
    private final Clock clock;

    public ProviderEventProcessor(
            JdbcTemplate jdbc,
            TransactionTemplate tx,
            PaymentIntentService intents,
            PaymentProvider provider,
            Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.intents = intents;
        this.provider = provider;
        this.clock = clock;
    }

    /** Returns false when no linked notification is due. */
    public boolean processNext() {
        var candidate = next();
        if (candidate.isEmpty()) {
            return false;
        }
        var event = candidate.get();
        if (!"CHECKOUT_PAID".equals(event.type())) {
            tx.executeWithoutResult(status -> finish(event, "PROCESSED", null));
            return true;
        }
        Optional<CheckoutState> state;
        try {
            state = provider.findCheckout(event.intentId());
        } catch (RuntimeException failure) {
            tx.executeWithoutResult(status -> retryLater(event, "LOOKUP_FAILED"));
            return true;
        }
        tx.executeWithoutResult(status -> apply(event, state));
        return true;
    }

    private void apply(Candidate event, Optional<CheckoutState> state) {
        var paid = state.filter(s -> s.status() == CheckoutStatus.PAID);
        if (paid.isEmpty()) {
            retryLater(event, "NOT_PAID_AT_PROVIDER");
            return;
        }
        if (!paid.get().checkoutId().equals(event.checkoutId())) {
            // The intent was paid through another checkout: money may have arrived, but not through this one.
            if (finish(event, "REVIEW", "CHECKOUT_MISMATCH")) {
                moveIntent(event, PaymentStatus.UNDER_REVIEW, "CHECKOUT_MISMATCH");
            }
            return;
        }
        if (!finish(event, "PROCESSED", null)) {
            return;
        }
        var amountMatches = paid.get().amountCents() == event.amountCents();
        moveIntent(
                event,
                amountMatches ? PaymentStatus.CONFIRMED : PaymentStatus.UNDER_REVIEW,
                amountMatches ? null : "AMOUNT_MISMATCH");
    }

    /** Out-of-order or repeated facts never move an intent that already left the waiting states. */
    private void moveIntent(Candidate event, PaymentStatus target, String reason) {
        var current = PaymentStatus.valueOf(jdbc.queryForObject(
                "SELECT status FROM payment_intent WHERE id = ? FOR UPDATE", String.class, event.intentId()));
        if (current == PaymentStatus.AWAITING_PAYMENT || current == PaymentStatus.UNKNOWN) {
            intents.transition(event.intentId(), target, reason, UUID.randomUUID());
        }
    }

    /** Settles the notification once; false when another worker already did. */
    private boolean finish(Candidate event, String status, String error) {
        return jdbc.update(
                        "UPDATE payment_provider_event SET status = ?, last_error = ?, processed_at = ?"
                                + " WHERE provider = 'ASAAS' AND event_id = ? AND status = 'RECEIVED'"
                                + " AND attempts = ?",
                        status,
                        error,
                        Timestamp.from(clock.instant()),
                        event.eventId(),
                        event.attempts())
                == 1;
    }

    private void retryLater(Candidate event, String error) {
        var attempts = event.attempts() + 1;
        if (attempts >= MAX_ATTEMPTS) {
            jdbc.update(
                    "UPDATE payment_provider_event SET status = 'REVIEW', attempts = ?, last_error = ?,"
                            + " next_attempt_at = NULL, processed_at = ? WHERE provider = 'ASAAS' AND event_id = ?"
                            + " AND status = 'RECEIVED' AND attempts = ?",
                    attempts,
                    error,
                    Timestamp.from(clock.instant()),
                    event.eventId(),
                    event.attempts());
            return;
        }
        jdbc.update(
                "UPDATE payment_provider_event SET attempts = ?, last_error = ?, next_attempt_at = ?"
                        + " WHERE provider = 'ASAAS' AND event_id = ? AND status = 'RECEIVED' AND attempts = ?",
                attempts,
                error,
                Timestamp.from(clock.instant().plus(delay(attempts))),
                event.eventId(),
                event.attempts());
    }

    /**
     * Exponential delay with jitter between half and the whole step. Unlike the outbox's full jitter, a zero delay
     * would spend several attempts within one worker tick before the provider had any chance to catch up.
     */
    static Duration delay(int attempts) {
        var step = BASE_DELAY.multipliedBy(1L << Math.min(attempts - 1, 16));
        var cap = step.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : step;
        var half = cap.toMillis() / 2;
        return Duration.ofMillis(half + ThreadLocalRandom.current().nextLong(cap.toMillis() - half + 1));
    }

    private Optional<Candidate> next() {
        return jdbc
                .query(
                        """
                        SELECT e.event_id, e.event_type, e.checkout_id, e.attempts, i.id AS intent_id,
                               i.amount_cents
                        FROM payment_provider_event e
                        JOIN payment_intent i ON i.provider_checkout_id = e.checkout_id
                        WHERE e.provider = 'ASAAS' AND e.status = 'RECEIVED'
                          AND (e.next_attempt_at IS NULL OR e.next_attempt_at <= ?)
                        ORDER BY e.received_at, e.event_id LIMIT 1
                        """,
                        (rs, row) -> new Candidate(
                                rs.getString("event_id"),
                                rs.getString("event_type"),
                                rs.getString("checkout_id"),
                                rs.getInt("attempts"),
                                rs.getObject("intent_id", UUID.class),
                                rs.getLong("amount_cents")),
                        Timestamp.from(clock.instant()))
                .stream()
                .findFirst();
    }

    private record Candidate(
            String eventId, String type, String checkoutId, int attempts, UUID intentId, long amountCents) {}
}
