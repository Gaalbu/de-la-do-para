package br.com.deladopara.payments.application;

import br.com.deladopara.payments.application.PaymentProvider.CheckoutStatus;
import br.com.deladopara.payments.domain.PaymentStatus;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns stored provider notifications into payment facts. A notification is never trusted on its own: the provider
 * is queried outside any transaction, and only a PAID state for the exact amount confirms the payment (V09).
 * Notifications for checkouts not yet linked to an intent stay RECEIVED for reconciliation.
 */
public class ProviderEventProcessor {

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

    /** Returns false when no linked notification is waiting. */
    public boolean processNext() {
        var candidate = next();
        if (candidate.isEmpty()) {
            return false;
        }
        var event = candidate.get();
        var state = "CHECKOUT_PAID".equals(event.type())
                ? provider.findCheckout(event.intentId())
                : Optional.<PaymentProvider.CheckoutState>empty();
        tx.executeWithoutResult(status -> apply(event, state));
        return true;
    }

    private void apply(Candidate event, Optional<PaymentProvider.CheckoutState> state) {
        var locked = jdbc.update(
                "UPDATE payment_provider_event SET status = ?, processed_at = ? WHERE provider = 'ASAAS'"
                        + " AND event_id = ? AND status = 'RECEIVED'",
                outcome(event, state),
                Timestamp.from(clock.instant()),
                event.eventId());
        if (locked == 0 || !"CHECKOUT_PAID".equals(event.type())) {
            return;
        }
        var paid = state.filter(
                s -> s.status() == CheckoutStatus.PAID && s.checkoutId().equals(event.checkoutId()));
        if (paid.isEmpty()) {
            return;
        }
        var current = PaymentStatus.valueOf(jdbc.queryForObject(
                "SELECT status FROM payment_intent WHERE id = ? FOR UPDATE", String.class, event.intentId()));
        if (current != PaymentStatus.AWAITING_PAYMENT && current != PaymentStatus.UNKNOWN) {
            return;
        }
        var target =
                paid.get().amountCents() == event.amountCents() ? PaymentStatus.CONFIRMED : PaymentStatus.UNDER_REVIEW;
        intents.transition(
                event.intentId(),
                target,
                target == PaymentStatus.CONFIRMED ? null : "AMOUNT_MISMATCH",
                UUID.randomUUID());
    }

    private static String outcome(Candidate event, Optional<PaymentProvider.CheckoutState> state) {
        if (!"CHECKOUT_PAID".equals(event.type())) {
            return "PROCESSED";
        }
        return state.filter(s -> s.status() == CheckoutStatus.PAID).isPresent() ? "PROCESSED" : "IGNORED";
    }

    private Optional<Candidate> next() {
        return jdbc
                .query(
                        """
                        SELECT e.event_id, e.event_type, e.checkout_id, i.id AS intent_id, i.amount_cents
                        FROM payment_provider_event e
                        JOIN payment_intent i ON i.provider_checkout_id = e.checkout_id
                        WHERE e.provider = 'ASAAS' AND e.status = 'RECEIVED'
                        ORDER BY e.received_at, e.event_id LIMIT 1
                        """,
                        (rs, row) -> new Candidate(
                                rs.getString("event_id"),
                                rs.getString("event_type"),
                                rs.getString("checkout_id"),
                                rs.getObject("intent_id", UUID.class),
                                rs.getLong("amount_cents")))
                .stream()
                .findFirst();
    }

    private record Candidate(String eventId, String type, String checkoutId, UUID intentId, long amountCents) {}
}
