package br.com.deladopara.payments.adapter.simulated;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.payments.adapter.simulated.SimulatedPaymentProvider.Outcome;
import br.com.deladopara.payments.application.CheckoutOperationRunner;
import br.com.deladopara.payments.application.CheckoutOperations;
import br.com.deladopara.payments.application.PaymentIntentService;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutStatus;
import br.com.deladopara.support.PostgresTestContainer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "payments.provider=simulated")
@Import(PostgresTestContainer.class)
class SimulatedCheckoutIT {

    private final PaymentIntentService intents;
    private final CheckoutOperations operations;
    private final SimulatedPaymentProvider simulator;
    private final JdbcTemplate jdbc;

    @Autowired
    SimulatedCheckoutIT(
            PaymentIntentService intents,
            CheckoutOperations operations,
            SimulatedPaymentProvider simulator,
            JdbcTemplate jdbc) {
        this.intents = intents;
        this.operations = operations;
        this.simulator = simulator;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("ALTER TABLE payment_intent DISABLE TRIGGER payment_intent_reference_guard");
        jdbc.execute("TRUNCATE payment_external_operation, payment_intent, event_outbox CASCADE");
        jdbc.execute("ALTER TABLE payment_intent ENABLE TRIGGER payment_intent_reference_guard");
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM payment_intent WHERE id = ?", String.class, id);
    }

    @Test
    void simulatedModeCreatesAHostedCheckoutLink() {
        var id = intents.request(UUID.randomUUID(), 5_250, UUID.randomUUID());

        new CheckoutOperationRunner(operations, simulator).runNext();

        assertThat(status(id)).isEqualTo("AWAITING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT checkout_url FROM payment_intent WHERE id = ?", String.class, id))
                .startsWith("https://simulado.local/checkout/");
    }

    @Test
    void timeoutAfterEffectLeavesUnknownWithOneCheckoutAtTheProvider() {
        var id = intents.request(UUID.randomUUID(), 5_250, UUID.randomUUID());
        simulator.failNextCreate(Outcome.TIMEOUT_AFTER_EFFECT);
        var runner = new CheckoutOperationRunner(operations, simulator);

        runner.runNext();
        runner.runNext();

        assertThat(status(id)).isEqualTo("UNKNOWN");
        assertThat(simulator.findCheckout(id).orElseThrow().status()).isEqualTo(CheckoutStatus.PENDING);
        assertThat(jdbc.queryForObject("SELECT checkout_url FROM payment_intent WHERE id = ?", String.class, id))
                .isNull();
    }
}
