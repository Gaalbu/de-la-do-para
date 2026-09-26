package br.com.deladopara.checkout.application;

import static br.com.deladopara.checkout.application.CheckoutIdempotency.PURCHASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.checkout.application.CheckoutIdempotency.Claim;
import br.com.deladopara.checkout.application.CheckoutIdempotency.IdempotencyKeyReusedException;
import br.com.deladopara.checkout.application.CheckoutIdempotency.InvalidIdempotencyKeyException;
import br.com.deladopara.checkout.application.CheckoutIdempotency.Outcome;
import br.com.deladopara.support.PostgresTestContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(PostgresTestContainer.class)
class CheckoutIdempotencyIT {

    private static final String KEY = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";
    private static final UUID SNAPSHOT = UUID.fromString("7d0c1c5e-6f4a-4d43-8a51-3f3a1f8b2e10");

    private final CheckoutIdempotency idempotency;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    @Autowired
    CheckoutIdempotencyIT(CheckoutIdempotency idempotency, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.idempotency = idempotency;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE checkout_idempotency");
    }

    private static String hash(String email) {
        return CheckoutIdempotency.purchaseHash(SNAPSHOT, 3, "delivery:quote-1", email, "BEMVINDO", "summary-v1");
    }

    /** Claims and, when new, completes with a fresh order in the same transaction. */
    private Claim accept(String subject, String key, String requestHash) {
        return tx.execute(status -> {
            var claim = idempotency.claim(subject, PURCHASE, key, requestHash);
            if (claim.outcome() == Outcome.NEW) {
                var orderId = UUID.randomUUID();
                idempotency.complete(subject, PURCHASE, key, orderId);
                return new Claim(Outcome.NEW, orderId);
            }
            return claim;
        });
    }

    @Test
    void sameKeyAndIntentReplaysTheSameOrder() {
        var first = accept("guest:s1", KEY, hash("ana@example.com"));
        var replay = accept("guest:s1", KEY, hash("ana@example.com"));

        assertThat(first.outcome()).isEqualTo(Outcome.NEW);
        assertThat(replay.outcome()).isEqualTo(Outcome.REPLAY);
        assertThat(replay.orderId()).isEqualTo(first.orderId());
    }

    @Test
    void changedBodyConflictsAndSubjectsAreIsolated() {
        var first = accept("guest:s1", KEY, hash("ana@example.com"));

        assertThatThrownBy(() -> accept("guest:s1", KEY, hash("bia@example.com")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        var otherSubject = accept("account:42", KEY, hash("bia@example.com"));

        assertThat(otherSubject.outcome()).isEqualTo(Outcome.NEW);
        assertThat(otherSubject.orderId()).isNotEqualTo(first.orderId());
    }

    @Test
    void rollbackRemovesTheClaimSoTheBuyerCanRetry() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    idempotency.claim("guest:s1", PURCHASE, KEY, hash("ana@example.com"));
                    throw new IllegalStateException("out of stock");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(accept("guest:s1", KEY, hash("ana@example.com")).outcome()).isEqualTo(Outcome.NEW);
    }

    @Test
    void concurrentClaimsOfOneKeyProduceOneOrder() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        var claims = new ArrayList<Claim>();
        try {
            var calls = new ArrayList<Callable<Claim>>();
            for (int i = 0; i < 8; i++) {
                calls.add(() -> {
                    start.await();
                    return accept("guest:s1", KEY, hash("ana@example.com"));
                });
            }
            var futures = calls.stream().map(pool::submit).toList();
            start.countDown();
            for (var future : futures) {
                claims.add(future.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(claims.stream().filter(c -> c.outcome() == Outcome.NEW)).hasSize(1);
        assertThat(claims.stream().map(Claim::orderId).distinct()).hasSize(1);
    }

    @Test
    void malformedKeysAreRejected() {
        for (var key : List.of("", "short", "com espaço no meio 123456", "x".repeat(161))) {
            assertThatThrownBy(() -> accept("guest:s1", key, hash("ana@example.com")))
                    .as(key)
                    .isInstanceOf(InvalidIdempotencyKeyException.class);
        }
    }

    @Test
    void hashSeparatesFieldsAndNulls() {
        var base = CheckoutIdempotency.purchaseHash(SNAPSHOT, 3, "a", "b", null, "s");

        assertThat(base).hasSize(64);
        assertThat(CheckoutIdempotency.purchaseHash(SNAPSHOT, 3, "a", "b", "", "s"))
                .isNotEqualTo(base);
        assertThat(CheckoutIdempotency.purchaseHash(SNAPSHOT, 3, "ab", "", null, "s"))
                .isNotEqualTo(CheckoutIdempotency.purchaseHash(SNAPSHOT, 3, "a", "b", null, "s"));
        assertThat(CheckoutIdempotency.purchaseHash(SNAPSHOT, 4, "a", "b", null, "s"))
                .isNotEqualTo(base);
    }
}
