package br.com.deladopara.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.payments.application.PaymentProvider.CheckoutState;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutStatus;
import br.com.deladopara.support.PostgresTestContainer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** C64: webhook facts are checked against the provider's state, and a lookup that proves nothing is not final. */
@SpringBootTest
@Import(PostgresTestContainer.class)
class PaymentReconciliationIT {

    private static final long AMOUNT = 4_200;

    private final PaymentIntentService intents;
    private final CheckoutOperations operations;
    private final ProviderEventInbox inbox;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MutableClock clock = new MutableClock(Instant.now());
    private final Map<UUID, Supplier<Optional<CheckoutState>>> lookups = new ConcurrentHashMap<>();
    private final ProviderEventProcessor processor;

    @Autowired
    PaymentReconciliationIT(
            PaymentIntentService intents,
            CheckoutOperations operations,
            ProviderEventInbox inbox,
            JdbcTemplate jdbc,
            TransactionTemplate tx) {
        this.intents = intents;
        this.operations = operations;
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.tx = tx;
        this.processor = new ProviderEventProcessor(jdbc, tx, intents, provider(), clock);
    }

    @BeforeEach
    void clean() {
        jdbc.execute("ALTER TABLE payment_intent DISABLE TRIGGER payment_intent_reference_guard");
        jdbc.execute("TRUNCATE payment_provider_event, payment_external_operation, payment_intent, event_outbox"
                + " CASCADE");
        jdbc.execute("ALTER TABLE payment_intent ENABLE TRIGGER payment_intent_reference_guard");
    }

    /** Creation answers with {@code chk_<intent>}; lookups answer whatever the scenario set. */
    private PaymentProvider provider() {
        return new PaymentProvider() {
            @Override
            public CreatedCheckout createCheckout(CheckoutRequest request) {
                return new CreatedCheckout(
                        "chk_" + request.paymentIntentId(),
                        "https://sandbox.example/c/" + request.paymentIntentId(),
                        Instant.now().plusSeconds(600));
            }

            @Override
            public Optional<CheckoutState> findCheckout(UUID paymentIntentId) {
                return lookups.getOrDefault(paymentIntentId, Optional::empty).get();
            }
        };
    }

    private UUID awaitingPayment() {
        var intentId = intents.request(UUID.randomUUID(), AMOUNT, UUID.randomUUID());
        assertThat(new CheckoutOperationRunner(operations, provider()).runNext())
                .isTrue();
        assertThat(intentStatus(intentId)).isEqualTo("AWAITING_PAYMENT");
        return intentId;
    }

    private static String checkout(UUID intentId) {
        return "chk_" + intentId;
    }

    private void notify(String eventId, String type, UUID intentId) {
        inbox.record("ASAAS", new ProviderEventInbox.Notification(eventId, type, checkout(intentId), null, null));
    }

    private void providerShows(UUID intentId, String checkoutId, CheckoutStatus status, long amount) {
        lookups.put(intentId, () -> Optional.of(new CheckoutState(checkoutId, status, amount)));
    }

    private String intentStatus(UUID intentId) {
        return jdbc.queryForObject("SELECT status FROM payment_intent WHERE id = ?", String.class, intentId);
    }

    private Map<String, Object> event(String eventId) {
        return jdbc.queryForMap(
                "SELECT status, attempts, last_error, next_attempt_at FROM payment_provider_event WHERE event_id = ?",
                eventId);
    }

    @Test
    void paymentNotYetVisibleIsRetriedUntilTheProviderShowsIt() {
        var intentId = awaitingPayment();
        notify("evt_early", "CHECKOUT_PAID", intentId);
        providerShows(intentId, checkout(intentId), CheckoutStatus.PENDING, AMOUNT);

        assertThat(processor.processNext()).isTrue();
        assertThat(event("evt_early"))
                .containsEntry("status", "RECEIVED")
                .containsEntry("attempts", 1)
                .containsEntry("last_error", "NOT_PAID_AT_PROVIDER");
        assertThat(processor.processNext()).as("not due before the backoff").isFalse();

        providerShows(intentId, checkout(intentId), CheckoutStatus.PAID, AMOUNT);
        clock.advance(Duration.ofSeconds(2));

        assertThat(processor.processNext()).isTrue();
        assertThat(event("evt_early")).containsEntry("status", "PROCESSED");
        assertThat(intentStatus(intentId)).isEqualTo("CONFIRMED");
    }

    @Test
    void lookupFailureIsRetriedAndNeverReadAsNotPaid() {
        var intentId = awaitingPayment();
        notify("evt_down", "CHECKOUT_PAID", intentId);
        lookups.put(intentId, () -> {
            throw new IllegalStateException("provider down");
        });

        assertThat(processor.processNext()).isTrue();

        assertThat(event("evt_down"))
                .containsEntry("status", "RECEIVED")
                .containsEntry("attempts", 1)
                .containsEntry("last_error", "LOOKUP_FAILED");
        assertThat(intentStatus(intentId)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void exhaustedLookupsStayVisibleForReviewWithoutTouchingThePayment() {
        var intentId = awaitingPayment();
        notify("evt_never", "CHECKOUT_PAID", intentId);

        for (int attempt = 0; attempt < ProviderEventProcessor.MAX_ATTEMPTS; attempt++) {
            assertThat(processor.processNext()).isTrue();
            clock.advance(ProviderEventProcessor.MAX_DELAY);
        }

        assertThat(processor.processNext()).isFalse();
        assertThat(event("evt_never"))
                .containsEntry("status", "REVIEW")
                .containsEntry("attempts", ProviderEventProcessor.MAX_ATTEMPTS)
                .containsEntry("last_error", "NOT_PAID_AT_PROVIDER");
        assertThat(intentStatus(intentId)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void paymentThroughAnotherCheckoutGoesToReview() {
        var intentId = awaitingPayment();
        notify("evt_other", "CHECKOUT_PAID", intentId);
        providerShows(intentId, "chk_somewhere_else", CheckoutStatus.PAID, AMOUNT);

        processor.processNext();

        assertThat(event("evt_other"))
                .containsEntry("status", "REVIEW")
                .containsEntry("last_error", "CHECKOUT_MISMATCH");
        assertThat(intentStatus(intentId)).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void lateOrRepeatedNotificationsNeverRegressAConfirmedPayment() {
        var intentId = awaitingPayment();
        providerShows(intentId, checkout(intentId), CheckoutStatus.PAID, AMOUNT);
        notify("evt_paid", "CHECKOUT_PAID", intentId);
        processor.processNext();
        assertThat(intentStatus(intentId)).isEqualTo("CONFIRMED");

        notify("evt_expired_late", "CHECKOUT_EXPIRED", intentId);
        notify("evt_paid_again", "CHECKOUT_PAID", intentId);
        providerShows(intentId, checkout(intentId), CheckoutStatus.PAID, AMOUNT - 1);
        while (processor.processNext()) {
            // drain
        }

        assertThat(intentStatus(intentId)).isEqualTo("CONFIRMED");
        assertThat(event("evt_expired_late")).containsEntry("status", "PROCESSED");
        assertThat(event("evt_paid_again")).containsEntry("status", "PROCESSED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM event_outbox WHERE aggregate_id = ? AND payload->>'to' = 'CONFIRMED'",
                        Integer.class,
                        intentId.toString()))
                .isEqualTo(1);
    }

    @Test
    void aNotificationWaitingForRetryDoesNotBlockTheNextOne() {
        var waiting = awaitingPayment();
        var paid = awaitingPayment();
        notify("evt_waiting", "CHECKOUT_PAID", waiting);
        notify("evt_ready", "CHECKOUT_PAID", paid);
        providerShows(paid, checkout(paid), CheckoutStatus.PAID, AMOUNT);

        assertThat(processor.processNext()).isTrue();
        assertThat(processor.processNext()).isTrue();

        assertThat(intentStatus(paid)).isEqualTo("CONFIRMED");
        assertThat(event("evt_waiting")).containsEntry("status", "RECEIVED");
    }

    @Test
    void retryDelayDoublesWithinHalfAndWholeStepUpToOneMinute() {
        for (int attempts = 1; attempts <= 10; attempts++) {
            var step = Duration.ofSeconds(Math.min(1L << (attempts - 1), 60));
            var delay = ProviderEventProcessor.delay(attempts);
            assertThat(delay).isBetween(step.dividedBy(2), step);
        }
    }

    private static final class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
