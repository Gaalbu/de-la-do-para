package br.com.deladopara.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.payments.application.PaymentProvider.CheckoutRequest;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutState;
import br.com.deladopara.payments.application.PaymentProvider.CreatedCheckout;
import br.com.deladopara.payments.application.PaymentProvider.ProviderRejectedException;
import br.com.deladopara.support.PostgresTestContainer;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@Import(PostgresTestContainer.class)
class CheckoutOperationRunnerIT {

    private static final Instant EXPIRES = Instant.parse("2026-09-24T12:15:00Z");

    private final PaymentIntentService intents;
    private final CheckoutOperations operations;
    private final JdbcTemplate jdbc;

    @Autowired
    CheckoutOperationRunnerIT(PaymentIntentService intents, CheckoutOperations operations, JdbcTemplate jdbc) {
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

    private static PaymentProvider creating(Function<CheckoutRequest, CreatedCheckout> create) {
        return new PaymentProvider() {
            @Override
            public CreatedCheckout createCheckout(CheckoutRequest request) {
                return create.apply(request);
            }

            @Override
            public Optional<CheckoutState> findCheckout(UUID paymentIntentId) {
                return Optional.empty();
            }
        };
    }

    private String intentStatus(UUID id) {
        return jdbc.queryForObject("SELECT status FROM payment_intent WHERE id = ?", String.class, id);
    }

    private String operationStatus(UUID intentId) {
        return jdbc.queryForObject(
                "SELECT status FROM payment_external_operation WHERE intent_id = ?", String.class, intentId);
    }

    private java.util.List<String> events(UUID id) {
        return jdbc.queryForList(
                "SELECT event_type || ':' || aggregate_version FROM event_outbox WHERE aggregate_id = ?"
                        + " ORDER BY aggregate_version",
                String.class,
                id.toString());
    }

    @Test
    void operationIsDurableAndInFlightBeforeProviderCallWhichRunsWithoutTransaction() {
        var id = intents.request(UUID.randomUUID(), 5_250, UUID.randomUUID());
        var seen = new StringBuilder();
        PaymentProvider provider = creating(request -> {
            seen.append(TransactionSynchronizationManager.isActualTransactionActive())
                    .append(':')
                    .append(operationStatus(request.paymentIntentId()))
                    .append(':')
                    .append(intentStatus(request.paymentIntentId()))
                    .append(':')
                    .append(request.amountCents());
            return new CreatedCheckout("chk_1", "https://sandbox.example/c/chk_1", EXPIRES);
        });

        assertThat(new CheckoutOperationRunner(operations, provider).runNext()).isTrue();

        assertThat(seen).hasToString("false:IN_FLIGHT:CREATING_CHECKOUT:5250");
        assertThat(intentStatus(id)).isEqualTo("AWAITING_PAYMENT");
        assertThat(operationStatus(id)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT checkout_url FROM payment_intent WHERE id = ?", String.class, id))
                .isEqualTo("https://sandbox.example/c/chk_1");
        assertThat(events(id)).containsExactly("payment.checkout_requested:0", "payment.checkout_available:1");
    }

    @Test
    void timeoutAfterSendingKeepsUnknownAndIsNeverRetried() {
        var id = intents.request(UUID.randomUUID(), 5_250, UUID.randomUUID());
        var calls = new AtomicInteger();
        PaymentProvider provider = creating(request -> {
            calls.incrementAndGet();
            throw new UncheckedIOException(new SocketTimeoutException("read timed out"));
        });
        var runner = new CheckoutOperationRunner(operations, provider);

        assertThat(runner.runNext()).isTrue();
        assertThat(runner.runNext()).isFalse();

        assertThat(calls).hasValue(1);
        assertThat(intentStatus(id)).isEqualTo("UNKNOWN");
        assertThat(operationStatus(id)).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject(
                        "SELECT last_error FROM payment_external_operation WHERE intent_id = ?", String.class, id))
                .isEqualTo("UNKNOWN:UncheckedIOException");
    }

    @Test
    void rejectionBeforeEffectDeclinesTheIntent() {
        var id = intents.request(UUID.randomUUID(), 5_250, UUID.randomUUID());
        PaymentProvider provider = creating(request -> {
            throw new ProviderRejectedException("HTTP_400");
        });

        new CheckoutOperationRunner(operations, provider).runNext();

        assertThat(intentStatus(id)).isEqualTo("DECLINED");
        assertThat(operationStatus(id)).isEqualTo("FAILED");
        assertThat(events(id)).containsExactly("payment.checkout_requested:0", "payment.status_changed:1");
    }

    @Test
    void expiredLeaseBecomesUnknownInsteadOfBeingClaimedAgain() {
        var id = intents.request(UUID.randomUUID(), 5_250, UUID.randomUUID());
        var claimed = operations.claim().orElseThrow();
        jdbc.update(
                "UPDATE payment_external_operation SET lease_until = now() - interval '1 second' WHERE id = ?",
                claimed.operationId());

        assertThat(operations.claim()).isEmpty();
        assertThat(operations.recoverAbandoned()).isEqualTo(1);
        operations.recordCreated(claimed, new CreatedCheckout("late", "https://late", EXPIRES), UUID.randomUUID());

        assertThat(intentStatus(id)).isEqualTo("UNKNOWN");
        assertThat(operationStatus(id)).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT checkout_url FROM payment_intent WHERE id = ?", String.class, id))
                .isNull();
    }

    @Test
    void nothingToRunReturnsFalse() {
        PaymentProvider provider = creating(request -> {
            throw new AssertionError("must not be called");
        });

        assertThat(new CheckoutOperationRunner(operations, provider).runNext()).isFalse();
    }
}
