package br.com.deladopara.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.payments.adapter.simulated.SimulatedPaymentProvider;
import br.com.deladopara.payments.adapter.simulated.SimulatedPaymentProvider.Outcome;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutState;
import br.com.deladopara.support.PostgresTestContainer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** C65: an UNKNOWN creation is resolved only by lookups, never by creating again (V07/V19). */
@SpringBootTest
@Import(PostgresTestContainer.class)
class UnknownPaymentRecoveryIT {

    private static final long AMOUNT = 5_250;

    private final PaymentIntentService intents;
    private final CheckoutOperations operations;
    private final UnknownPaymentLookups lookups;
    private final ProviderEventInbox inbox;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final SimulatedPaymentProvider simulator = new SimulatedPaymentProvider(Clock.systemUTC());
    private final AtomicInteger creations = new AtomicInteger();
    private final AtomicInteger finds = new AtomicInteger();
    private final AtomicBoolean lookupsFail = new AtomicBoolean();

    @Autowired
    UnknownPaymentRecoveryIT(
            PaymentIntentService intents,
            CheckoutOperations operations,
            UnknownPaymentLookups lookups,
            ProviderEventInbox inbox,
            JdbcTemplate jdbc,
            TransactionTemplate tx) {
        this.intents = intents;
        this.operations = operations;
        this.lookups = lookups;
        this.inbox = inbox;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("ALTER TABLE payment_intent DISABLE TRIGGER payment_intent_reference_guard");
        jdbc.execute("TRUNCATE payment_provider_event, payment_external_operation, payment_intent, event_outbox"
                + " CASCADE");
        jdbc.execute("ALTER TABLE payment_intent ENABLE TRIGGER payment_intent_reference_guard");
    }

    /** The simulator, counting creations and lookups; lookups can be made to fail. */
    private PaymentProvider provider() {
        return new PaymentProvider() {
            @Override
            public CreatedCheckout createCheckout(CheckoutRequest request) {
                creations.incrementAndGet();
                return simulator.createCheckout(request);
            }

            @Override
            public Optional<CheckoutState> findCheckout(UUID paymentIntentId) {
                finds.incrementAndGet();
                if (lookupsFail.get()) {
                    throw new IllegalStateException("provider unavailable");
                }
                return simulator.findCheckout(paymentIntentId);
            }
        };
    }

    private UnknownPaymentReconciler reconciler() {
        return new UnknownPaymentReconciler(lookups, provider());
    }

    private UUID unknownIntent(Outcome creation) {
        var intentId = intents.request(UUID.randomUUID(), AMOUNT, UUID.randomUUID());
        simulator.failNextCreate(creation);
        new CheckoutOperationRunner(operations, provider()).runNext();
        assertThat(status(intentId)).isEqualTo("UNKNOWN");
        return intentId;
    }

    /** Moves every timestamp the backoff reads past the longest delay. */
    private void waitOutBackoff() {
        jdbc.update("UPDATE payment_intent SET updated_at = updated_at - interval '2 minutes'");
        jdbc.update("UPDATE payment_external_operation SET finished_at = finished_at - interval '2 minutes'"
                + " WHERE kind = 'QUERY'");
    }

    private String status(UUID intentId) {
        return jdbc.queryForObject("SELECT status FROM payment_intent WHERE id = ?", String.class, intentId);
    }

    private String reason(UUID intentId) {
        return jdbc.queryForObject("SELECT status_reason FROM payment_intent WHERE id = ?", String.class, intentId);
    }

    private String linkedCheckout(UUID intentId) {
        return jdbc.queryForObject(
                "SELECT provider_checkout_id FROM payment_intent WHERE id = ?", String.class, intentId);
    }

    private List<String> lookupTrail(UUID intentId) {
        return jdbc.queryForList(
                "SELECT status || ' ' || last_error FROM payment_external_operation"
                        + " WHERE intent_id = ? AND kind = 'QUERY' ORDER BY started_at",
                String.class,
                intentId);
    }

    @Test
    void paidCheckoutBehindATimeoutIsConfirmedByLookupWithoutCreatingAgain() {
        var intentId = unknownIntent(Outcome.TIMEOUT_AFTER_EFFECT);
        var checkoutId = simulator.findCheckout(intentId).orElseThrow().checkoutId();
        simulator.pay(checkoutId, AMOUNT);
        waitOutBackoff();

        assertThat(reconciler().runNext()).isTrue();

        assertThat(status(intentId)).isEqualTo("CONFIRMED");
        assertThat(linkedCheckout(intentId)).isEqualTo(checkoutId);
        assertThat(creations).hasValue(1);
        assertThat(lookupTrail(intentId)).containsExactly("SUCCEEDED FOUND:PAID");
    }

    @Test
    void noLookupRunsBeforeTheBackoff() {
        unknownIntent(Outcome.TIMEOUT_AFTER_EFFECT);

        assertThat(reconciler().runNext()).isFalse();
        assertThat(finds).hasValue(0);
    }

    @Test
    void emptyAnswersAreNotProofAndOnlyTheLastOneSendsTheIntentToReview() {
        var intentId = unknownIntent(Outcome.TIMEOUT_BEFORE_EFFECT);
        var reconciler = reconciler();

        for (int lookup = 1; lookup <= 3; lookup++) {
            waitOutBackoff();
            assertThat(reconciler.runNext()).isTrue();
            assertThat(status(intentId)).isEqualTo(lookup < 3 ? "UNKNOWN" : "UNDER_REVIEW");
        }
        waitOutBackoff();

        assertThat(reconciler.runNext()).isFalse();
        assertThat(reason(intentId)).isEqualTo(UnknownPaymentLookups.UNRESOLVED);
        assertThat(lookupTrail(intentId))
                .containsExactly("SUCCEEDED NOT_FOUND", "SUCCEEDED NOT_FOUND", "SUCCEEDED NOT_FOUND");
        assertThat(creations).hasValue(1);
    }

    @Test
    void unpaidCheckoutIsLinkedSoItsLaterNotificationConfirms() {
        var intentId = unknownIntent(Outcome.TIMEOUT_AFTER_EFFECT);
        var checkoutId = simulator.findCheckout(intentId).orElseThrow().checkoutId();
        waitOutBackoff();

        reconciler().runNext();

        assertThat(status(intentId)).isEqualTo("UNKNOWN");
        assertThat(linkedCheckout(intentId)).isEqualTo(checkoutId);
        assertThat(lookupTrail(intentId)).containsExactly("SUCCEEDED FOUND:PENDING");

        simulator.pay(checkoutId, AMOUNT);
        inbox.record("ASAAS", new ProviderEventInbox.Notification("evt_late", "CHECKOUT_PAID", checkoutId, null, null));
        assertThat(new ProviderEventProcessor(jdbc, tx, intents, provider(), Clock.systemUTC()).processNext())
                .isTrue();

        assertThat(status(intentId)).isEqualTo("CONFIRMED");
        assertThat(creations).hasValue(1);
    }

    @Test
    void failedLookupIsAuditedAndProvesNothing() {
        var intentId = unknownIntent(Outcome.TIMEOUT_AFTER_EFFECT);
        lookupsFail.set(true);
        waitOutBackoff();

        assertThat(reconciler().runNext()).isTrue();

        assertThat(status(intentId)).isEqualTo("UNKNOWN");
        assertThat(lookupTrail(intentId)).containsExactly("FAILED LOOKUP_FAILED:IllegalStateException");
    }

    @Test
    void paidWithAnotherAmountGoesToReview() {
        var intentId = unknownIntent(Outcome.TIMEOUT_AFTER_EFFECT);
        simulator.pay(simulator.findCheckout(intentId).orElseThrow().checkoutId(), AMOUNT - 1);
        waitOutBackoff();

        reconciler().runNext();

        assertThat(status(intentId)).isEqualTo("UNDER_REVIEW");
        assertThat(reason(intentId)).isEqualTo("AMOUNT_MISMATCH");
    }

    @Test
    void abandonedLookupIsSettledWithoutTouchingTheIntent() {
        var intentId = unknownIntent(Outcome.TIMEOUT_AFTER_EFFECT);
        waitOutBackoff();
        lookups.claim().orElseThrow();
        jdbc.update("UPDATE payment_external_operation SET lease_until = now() - interval '1 second'"
                + " WHERE kind = 'QUERY'");

        assertThat(operations.recoverAbandoned()).isEqualTo(1);

        assertThat(status(intentId)).isEqualTo("UNKNOWN");
        assertThat(lookupTrail(intentId)).containsExactly("UNKNOWN LEASE_EXPIRED");
        assertThat(finds).hasValue(0);
    }

    @Test
    void concurrentWorkersRunOneLookupPerDueIntent() throws Exception {
        var intentId = unknownIntent(Outcome.TIMEOUT_AFTER_EFFECT);
        waitOutBackoff();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(3);
        try {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int worker = 0; worker < 3; worker++) {
                var reconciler = reconciler();
                tasks.add(() -> {
                    start.await();
                    return reconciler.runNext();
                });
            }
            var futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            for (var future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(finds).hasValue(1);
        assertThat(lookupTrail(intentId)).hasSize(1);
    }
}
