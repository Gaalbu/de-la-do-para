package br.com.deladopara.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.payments.domain.PaymentStatus;
import br.com.deladopara.payments.domain.PaymentTransitions.InvalidPaymentTransitionException;
import br.com.deladopara.support.PostgresTestContainer;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(PostgresTestContainer.class)
class PaymentIntentIT {

    private final PaymentIntentService service;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    @Autowired
    PaymentIntentIT(PaymentIntentService service, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.service = service;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("ALTER TABLE payment_intent DISABLE TRIGGER payment_intent_reference_guard");
        jdbc.execute("TRUNCATE payment_external_operation, payment_intent, event_outbox CASCADE");
        jdbc.execute("ALTER TABLE payment_intent ENABLE TRIGGER payment_intent_reference_guard");
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void requestWritesIntentPendingOperationAndEventAndIsIdempotentByOrder() {
        var orderId = UUID.randomUUID();

        var first = service.request(orderId, 5_250, UUID.randomUUID());
        var replay = service.request(orderId, 5_250, UUID.randomUUID());

        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT status FROM payment_intent WHERE id = ?", String.class, first))
                .isEqualTo("REQUESTED");
        assertThat(count(
                        "SELECT count(*) FROM payment_external_operation WHERE intent_id = ? AND kind = 'CREATE_CHECKOUT'"
                                + " AND status = 'PENDING'",
                        first))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM event_outbox WHERE event_type = 'payment.checkout_requested'"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT payload ->> 'amountCents' FROM event_outbox WHERE aggregate_id = ?",
                        String.class,
                        first.toString()))
                .isEqualTo("5250");
    }

    @Test
    void concurrentRequestsForTheSameOrderCreateOneIntent() throws Exception {
        var orderId = UUID.randomUUID();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        try {
            var calls = new ArrayList<Callable<UUID>>();
            for (int i = 0; i < 8; i++) {
                calls.add(() -> {
                    start.await();
                    return service.request(orderId, 5_250, UUID.randomUUID());
                });
            }
            var futures = calls.stream().map(pool::submit).toList();
            start.countDown();
            var ids = new java.util.HashSet<UUID>();
            for (var future : futures) {
                ids.add(future.get());
            }
            assertThat(ids).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("SELECT count(*) FROM payment_intent")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment_external_operation")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM event_outbox")).isEqualTo(1);
    }

    @Test
    void referenceAndAmountCannotChangeAndIntentCannotBeDeleted() {
        var id = service.request(UUID.randomUUID(), 5_250, UUID.randomUUID());

        assertThatThrownBy(() -> jdbc.update("UPDATE payment_intent SET amount_cents = 1 WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () -> jdbc.update("UPDATE payment_intent SET order_id = ? WHERE id = ?", UUID.randomUUID(), id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM payment_intent WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void secondCheckoutOperationForTheSameIntentIsRejected() {
        var id = service.request(UUID.randomUUID(), 5_250, UUID.randomUUID());

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO payment_external_operation (id, intent_id, kind, status, created_at)"
                                + " VALUES (?, ?, 'CREATE_CHECKOUT', 'PENDING', now())",
                        UUID.randomUUID(),
                        id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rollbackOfTheCallerDiscardsIntentOperationAndEvent() {
        var orderId = UUID.randomUUID();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    service.request(orderId, 5_250, UUID.randomUUID());
                    throw new IllegalStateException("checkout failed after the intent");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count("SELECT count(*) FROM payment_intent")).isZero();
        assertThat(count("SELECT count(*) FROM payment_external_operation")).isZero();
        assertThat(count("SELECT count(*) FROM event_outbox")).isZero();
    }

    @Test
    void transitionAdvancesVersionAndEmitsEventWhileInvalidOneChangesNothing() {
        var id = service.request(UUID.randomUUID(), 5_250, UUID.randomUUID());

        service.transition(id, PaymentStatus.CREATING_CHECKOUT, null, UUID.randomUUID());
        assertThatThrownBy(() -> service.transition(id, PaymentStatus.REFUNDED, null, UUID.randomUUID()))
                .isInstanceOf(InvalidPaymentTransitionException.class);

        assertThat(jdbc.queryForMap("SELECT status, status_version FROM payment_intent WHERE id = ?", id))
                .containsEntry("status", "CREATING_CHECKOUT")
                .containsEntry("status_version", 1);
        assertThat(count("SELECT count(*) FROM event_outbox WHERE event_type = 'payment.status_changed'"))
                .isEqualTo(1);
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> service.request(UUID.randomUUID(), 0, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
