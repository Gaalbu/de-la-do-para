package br.com.deladopara.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.payments.adapter.simulated.SimulatedPaymentProvider;
import br.com.deladopara.payments.adapter.simulated.SimulatedPaymentProvider.RefundOutcome;
import br.com.deladopara.payments.domain.PaymentStatus;
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

/** C67: a full refund is a durable operation, sent at most once and settled only by what the provider shows. */
@SpringBootTest
@Import(PostgresTestContainer.class)
class RefundLifecycleIT {

    private static final long AMOUNT = 5_250;

    private final PaymentIntentService intents;
    private final CheckoutOperations checkouts;
    private final RefundOperations refunds;
    private final JdbcTemplate jdbc;
    private final SimulatedPaymentProvider simulator = new SimulatedPaymentProvider(Clock.systemUTC());
    private final AtomicInteger refundCalls = new AtomicInteger();
    private final AtomicInteger refundLookups = new AtomicInteger();
    private final AtomicBoolean lookupsFail = new AtomicBoolean();

    @Autowired
    RefundLifecycleIT(
            PaymentIntentService intents, CheckoutOperations checkouts, RefundOperations refunds, JdbcTemplate jdbc) {
        this.intents = intents;
        this.checkouts = checkouts;
        this.refunds = refunds;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("ALTER TABLE payment_intent DISABLE TRIGGER payment_intent_reference_guard");
        jdbc.execute("TRUNCATE payment_provider_event, payment_external_operation, payment_intent, event_outbox"
                + " CASCADE");
        jdbc.execute("ALTER TABLE payment_intent ENABLE TRIGGER payment_intent_reference_guard");
    }

    /** The simulator, counting refund calls; lookups can be made to fail. */
    private RefundRunner runner() {
        return new RefundRunner(refunds, new RefundProvider() {
            @Override
            public RefundState refund(RefundRequest request) {
                refundCalls.incrementAndGet();
                return simulator.refund(request);
            }

            @Override
            public Optional<RefundState> findRefund(UUID paymentIntentId) {
                refundLookups.incrementAndGet();
                if (lookupsFail.get()) {
                    throw new IllegalStateException("provider unavailable");
                }
                return simulator.findRefund(paymentIntentId);
            }
        });
    }

    /** A paid intent whose refund was just requested, as the checkout does for a late payment (D13). */
    private UUID refundRequested() {
        var intentId = intents.request(UUID.randomUUID(), AMOUNT, UUID.randomUUID());
        new CheckoutOperationRunner(checkouts, simulator).runNext();
        simulator.pay(simulator.findCheckout(intentId).orElseThrow().checkoutId(), AMOUNT);
        intents.transition(intentId, PaymentStatus.CONFIRMED, null, UUID.randomUUID());
        intents.transition(intentId, PaymentStatus.REFUND_REQUESTED, "LATE_PAYMENT", UUID.randomUUID());
        return intentId;
    }

    /** Moves every timestamp the backoff reads past the longest delay. */
    private void waitOutBackoff() {
        jdbc.update("UPDATE payment_external_operation SET finished_at = finished_at - interval '2 minutes'"
                + " WHERE kind IN ('REFUND', 'QUERY')");
    }

    private String status(UUID intentId) {
        return jdbc.queryForObject("SELECT status FROM payment_intent WHERE id = ?", String.class, intentId);
    }

    private String refundOperation(UUID intentId) {
        return jdbc.queryForObject(
                "SELECT status || ' ' || coalesce(last_error, '') FROM payment_external_operation"
                        + " WHERE intent_id = ? AND kind = 'REFUND'",
                String.class,
                intentId);
    }

    private List<String> lookupTrail(UUID intentId) {
        return jdbc.queryForList(
                "SELECT status || ' ' || last_error FROM payment_external_operation q WHERE intent_id = ?"
                        + " AND kind = 'QUERY' ORDER BY started_at",
                String.class,
                intentId);
    }

    private List<String> events(UUID intentId) {
        return jdbc.queryForList(
                "SELECT event_type FROM event_outbox WHERE aggregate_id = ? AND event_type LIKE 'payment.refund%'"
                        + " ORDER BY aggregate_version",
                String.class, intentId.toString());
    }

    @Test
    void requestingARefundRecordsOneDurableOperationBeforeAnyCall() {
        var intentId = refundRequested();

        intents.transition(intentId, PaymentStatus.REFUND_REQUESTED, "LATE_PAYMENT", UUID.randomUUID());

        assertThat(refundOperation(intentId)).isEqualTo("PENDING ");
        assertThat(events(intentId)).containsExactly("payment.refund_requested");
        assertThat(refundCalls).hasValue(0);
    }

    @Test
    void settledRefundMarksTheIntentRefundedOnce() {
        var intentId = refundRequested();

        assertThat(runner().runNext()).isTrue();
        assertThat(runner().runNext()).isFalse();

        assertThat(status(intentId)).isEqualTo("REFUNDED");
        assertThat(refundOperation(intentId)).isEqualTo("SUCCEEDED REFUND:DONE");
        assertThat(events(intentId)).containsExactly("payment.refund_requested", "payment.refunded");
        assertThat(refundCalls).hasValue(1);
        waitOutBackoff();
        assertThat(runner().lookUpNext()).isFalse();
    }

    @Test
    void acceptedRefundIsFollowedByLookupsUntilTheProviderSettlesIt() {
        var intentId = refundRequested();
        simulator.failNextRefund(RefundOutcome.ACCEPTED);
        runner().runNext();
        assertThat(status(intentId)).isEqualTo("REFUND_REQUESTED");

        assertThat(runner().lookUpNext()).as("nothing before the backoff").isFalse();
        waitOutBackoff();
        assertThat(runner().lookUpNext()).isTrue();
        assertThat(status(intentId)).isEqualTo("REFUND_REQUESTED");

        simulator.settleRefund(intentId);
        waitOutBackoff();
        assertThat(runner().lookUpNext()).isTrue();

        assertThat(status(intentId)).isEqualTo("REFUNDED");
        assertThat(lookupTrail(intentId)).containsExactly("SUCCEEDED REFUND:PENDING", "SUCCEEDED REFUND:DONE");
        assertThat(refundCalls).hasValue(1);
    }

    @Test
    void lostAnswerAfterTheEffectIsResolvedByLookupAndNeverResent() {
        var intentId = refundRequested();
        simulator.failNextRefund(RefundOutcome.TIMEOUT_AFTER_EFFECT);
        runner().runNext();
        assertThat(refundOperation(intentId)).isEqualTo("UNKNOWN UNKNOWN:SimulatedTimeoutException");

        waitOutBackoff();
        assertThat(runner().runNext()).isFalse();
        assertThat(runner().lookUpNext()).isTrue();

        assertThat(status(intentId)).isEqualTo("REFUNDED");
        assertThat(refundCalls).hasValue(1);
    }

    @Test
    void refundNotFoundAfterALostRequestStaysInReconciliation() {
        var intentId = refundRequested();
        simulator.failNextRefund(RefundOutcome.TIMEOUT_BEFORE_EFFECT);
        runner().runNext();

        waitOutBackoff();
        assertThat(runner().lookUpNext()).isTrue();
        lookupsFail.set(true);
        waitOutBackoff();
        assertThat(runner().lookUpNext()).isTrue();

        assertThat(status(intentId))
                .as("an empty or failed lookup proves nothing")
                .isEqualTo("REFUND_REQUESTED");
        assertThat(lookupTrail(intentId))
                .containsExactly("SUCCEEDED REFUND:NOT_FOUND", "FAILED LOOKUP_FAILED:IllegalStateException");
        assertThat(refundCalls).hasValue(1);
    }

    @Test
    void rejectedRefundIsLeftForTheOperatorWithoutLookups() {
        var intentId = refundRequested();
        simulator.failNextRefund(RefundOutcome.REJECTED);
        runner().runNext();

        waitOutBackoff();
        assertThat(runner().lookUpNext()).isFalse();
        assertThat(status(intentId)).isEqualTo("REFUND_REQUESTED");
        assertThat(refundOperation(intentId)).isEqualTo("FAILED REJECTED:HTTP_400");
    }

    @Test
    void concurrentWorkersSendTheRefundOnce() throws Exception {
        var intentId = refundRequested();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(3);
        try {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (var i = 0; i < 3; i++) {
                tasks.add(() -> {
                    start.await();
                    return runner().runNext();
                });
            }
            var futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            var ran = 0;
            for (var future : futures) {
                ran += future.get(30, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertThat(ran).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(refundCalls).hasValue(1);
        assertThat(status(intentId)).isEqualTo("REFUNDED");
    }

    @Test
    void concurrentWorkersRunOneLookupPerDueRefund() throws Exception {
        var intentId = refundRequested();
        simulator.failNextRefund(RefundOutcome.ACCEPTED);
        runner().runNext();
        waitOutBackoff();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(3);
        try {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (var i = 0; i < 3; i++) {
                var runner = runner();
                tasks.add(() -> {
                    start.await();
                    return runner.lookUpNext();
                });
            }
            var futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            for (var future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(refundLookups).hasValue(1);
        assertThat(lookupTrail(intentId)).hasSize(1);
    }
}
