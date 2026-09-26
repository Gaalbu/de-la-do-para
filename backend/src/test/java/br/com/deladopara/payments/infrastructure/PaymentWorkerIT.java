package br.com.deladopara.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.payments.adapter.simulated.SimulatedPaymentProvider;
import br.com.deladopara.payments.adapter.simulated.SimulatedPaymentProvider.Outcome;
import br.com.deladopara.payments.application.CheckoutOperations;
import br.com.deladopara.payments.application.PaymentIntentService;
import br.com.deladopara.payments.application.PaymentProvider;
import br.com.deladopara.support.PostgresTestContainer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresTestContainer.class)
class PaymentWorkerIT {

    private final PaymentIntentService intents;
    private final CheckoutOperations operations;
    private final JdbcTemplate jdbc;
    private final SimulatedPaymentProvider simulator = new SimulatedPaymentProvider(Clock.systemUTC());
    private final ConcurrentHashMap<UUID, AtomicInteger> calls = new ConcurrentHashMap<>();

    @Autowired
    PaymentWorkerIT(PaymentIntentService intents, CheckoutOperations operations, JdbcTemplate jdbc) {
        this.intents = intents;
        this.operations = operations;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("ALTER TABLE payment_intent DISABLE TRIGGER payment_intent_reference_guard");
        jdbc.execute("TRUNCATE payment_external_operation, payment_intent, event_outbox CASCADE");
        jdbc.execute("ALTER TABLE payment_intent ENABLE TRIGGER payment_intent_reference_guard");
    }

    /** Counts provider calls per intent around the simulator. */
    private PaymentProvider counting() {
        return new PaymentProvider() {
            @Override
            public CreatedCheckout createCheckout(CheckoutRequest request) {
                calls.computeIfAbsent(request.paymentIntentId(), id -> new AtomicInteger())
                        .incrementAndGet();
                return simulator.createCheckout(request);
            }

            @Override
            public Optional<CheckoutState> findCheckout(UUID paymentIntentId) {
                return simulator.findCheckout(paymentIntentId);
            }
        };
    }

    private int countStatus(String status) {
        return jdbc.queryForObject("SELECT count(*) FROM payment_intent WHERE status = ?", Integer.class, status);
    }

    @Test
    void tickRunsAtMostOneBatchOfPendingOperations() {
        for (int i = 0; i < 3; i++) {
            intents.request(UUID.randomUUID(), 1_000 + i, UUID.randomUUID());
        }
        var worker = new PaymentWorker(operations, counting(), 2);

        assertThat(worker.tick()).isEqualTo(2);
        assertThat(worker.tick()).isEqualTo(1);
        assertThat(worker.tick()).isZero();

        assertThat(countStatus("AWAITING_PAYMENT")).isEqualTo(3);
    }

    @Test
    void uncertainOutcomeStaysUnknownAcrossTicks() {
        var id = intents.request(UUID.randomUUID(), 5_250, UUID.randomUUID());
        simulator.failNextCreate(Outcome.TIMEOUT_AFTER_EFFECT);
        var worker = new PaymentWorker(operations, counting(), 10);

        worker.tick();
        worker.tick();

        assertThat(calls.get(id)).hasValue(1);
        assertThat(countStatus("UNKNOWN")).isEqualTo(1);
    }

    @Test
    void abandonedLeaseIsRecoveredAsUnknownWithoutCallingTheProviderAgain() {
        var id = intents.request(UUID.randomUUID(), 5_250, UUID.randomUUID());
        operations.claim().orElseThrow();
        jdbc.update("UPDATE payment_external_operation SET lease_until = now() - interval '1 second'");

        new PaymentWorker(operations, counting(), 10).tick();

        assertThat(calls).doesNotContainKey(id);
        assertThat(countStatus("UNKNOWN")).isEqualTo(1);
    }

    @Test
    void duplicatedWorkersCallTheProviderOncePerOperation() throws Exception {
        for (int i = 0; i < 12; i++) {
            intents.request(UUID.randomUUID(), 1_000 + i, UUID.randomUUID());
        }
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(3);
        try {
            var tasks = new ArrayList<Callable<Integer>>();
            for (int w = 0; w < 3; w++) {
                var worker = new PaymentWorker(operations, counting(), 50);
                tasks.add(() -> {
                    start.await();
                    return worker.tick();
                });
            }
            var futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            var total = 0;
            for (var future : futures) {
                total += future.get();
            }
            assertThat(total).isEqualTo(12);
        } finally {
            pool.shutdownNow();
        }
        assertThat(calls).hasSize(12);
        assertThat(calls.values()).allSatisfy(count -> assertThat(count).hasValue(1));
        assertThat(countStatus("AWAITING_PAYMENT")).isEqualTo(12);
    }
}
