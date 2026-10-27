package br.com.deladopara.payments.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.deladopara.payments.adapter.simulated.SimulatedPaymentProvider;
import br.com.deladopara.payments.adapter.simulated.SimulatedPaymentProvider.Outcome;
import br.com.deladopara.payments.adapter.simulated.SimulatedPaymentProvider.RefundOutcome;
import br.com.deladopara.payments.application.AdminLookupRunner;
import br.com.deladopara.payments.application.AdminPaymentLookups;
import br.com.deladopara.payments.application.CheckoutOperationRunner;
import br.com.deladopara.payments.application.CheckoutOperations;
import br.com.deladopara.payments.application.PaymentIntentService;
import br.com.deladopara.payments.application.PaymentProvider;
import br.com.deladopara.payments.application.RefundOperations;
import br.com.deladopara.payments.application.RefundRunner;
import br.com.deladopara.payments.domain.PaymentStatus;
import br.com.deladopara.support.PostgresTestContainer;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** C82a: an administrator queues audited lookups for uncertain payments; nothing recreates a charge or refund. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class AdminPaymentOperationsIT {

    private static final long AMOUNT = 5_250;
    private static final String ADMIN = "admin@deladopara.local";

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final PaymentIntentService intents;
    private final CheckoutOperations checkouts;
    private final RefundOperations refunds;
    private final AdminPaymentLookups lookups;
    private final SimulatedPaymentProvider simulator = new SimulatedPaymentProvider(Clock.systemUTC());
    private final AtomicInteger creations = new AtomicInteger();
    private final AtomicBoolean lookupsFail = new AtomicBoolean();

    @Autowired
    AdminPaymentOperationsIT(
            MockMvc mvc,
            JdbcTemplate jdbc,
            PaymentIntentService intents,
            CheckoutOperations checkouts,
            RefundOperations refunds,
            AdminPaymentLookups lookups) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.intents = intents;
        this.checkouts = checkouts;
        this.refunds = refunds;
        this.lookups = lookups;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("ALTER TABLE payment_intent DISABLE TRIGGER payment_intent_reference_guard");
        jdbc.execute("TRUNCATE payment_admin_lookup_request, payment_provider_event, payment_external_operation,"
                + " payment_intent, event_outbox CASCADE");
        jdbc.execute("ALTER TABLE payment_intent ENABLE TRIGGER payment_intent_reference_guard");
    }

    /** The simulator, counting creations; lookups can be made to fail. */
    private PaymentProvider provider() {
        return new PaymentProvider() {
            @Override
            public CreatedCheckout createCheckout(CheckoutRequest request) {
                creations.incrementAndGet();
                return simulator.createCheckout(request);
            }

            @Override
            public Optional<CheckoutState> findCheckout(UUID paymentIntentId) {
                if (lookupsFail.get()) {
                    throw new IllegalStateException("provider unavailable");
                }
                return simulator.findCheckout(paymentIntentId);
            }
        };
    }

    private AdminLookupRunner worker() {
        return new AdminLookupRunner(lookups, provider(), simulator);
    }

    private UUID unknownIntent() {
        var intentId = intents.request(UUID.randomUUID(), AMOUNT, UUID.randomUUID());
        simulator.failNextCreate(Outcome.TIMEOUT_AFTER_EFFECT);
        new CheckoutOperationRunner(checkouts, provider()).runNext();
        assertThat(intentStatus(intentId)).isEqualTo("UNKNOWN");
        return intentId;
    }

    private String checkoutOf(UUID intentId) {
        return simulator.findCheckout(intentId).orElseThrow().checkoutId();
    }

    private String intentStatus(UUID intentId) {
        return jdbc.queryForObject("SELECT status FROM payment_intent WHERE id = ?", String.class, intentId);
    }

    private int auditRows(UUID intentId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM payment_admin_lookup_request WHERE intent_id = ?", Integer.class, intentId);
    }

    private ResultActions requestLookup(UUID intentId, String reason) throws Exception {
        return mvc.perform(post("/api/v1/admin/payments/" + intentId + "/lookups")
                .with(user(ADMIN).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}"));
    }

    @Test
    void repeatedRequestReusesTheQueuedLookupWhichConfirmsWithoutCreatingAgain() throws Exception {
        var intentId = unknownIntent();
        simulator.pay(checkoutOf(intentId), AMOUNT);

        var first = requestLookup(intentId, "Cliente informou Pix pago")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.created").value(true))
                .andReturn();
        String operationId = JsonPath.read(first.getResponse().getContentAsString(), "$.operationId");
        requestLookup(intentId, "Cliente insistiu")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.operationId").value(operationId));
        assertThat(auditRows(intentId)).isEqualTo(1);

        assertThat(worker().runNext()).isTrue();
        assertThat(worker().runNext()).isFalse();

        assertThat(intentStatus(intentId)).isEqualTo("CONFIRMED");
        assertThat(creations).hasValue(1);
        mvc.perform(get("/api/v1/admin/payments/attention").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void divergentOrMissingPaymentNeverConfirms() throws Exception {
        var divergent = unknownIntent();
        simulator.pay(checkoutOf(divergent), AMOUNT - 1);
        var missing = intents.request(UUID.randomUUID(), AMOUNT, UUID.randomUUID());
        simulator.failNextCreate(Outcome.TIMEOUT_BEFORE_EFFECT);
        new CheckoutOperationRunner(checkouts, provider()).runNext();

        requestLookup(divergent, "Conferir valor").andExpect(status().isAccepted());
        requestLookup(missing, "Conferir criação").andExpect(status().isAccepted());
        worker().runNext();
        worker().runNext();

        assertThat(intentStatus(divergent)).isEqualTo("UNDER_REVIEW");
        assertThat(intentStatus(missing)).isEqualTo("UNKNOWN");
        assertThat(creations).hasValue(2);
    }

    @Test
    void refundLookupSettlesOnlyWhatTheProviderShowsDone() throws Exception {
        var intentId = intents.request(UUID.randomUUID(), AMOUNT, UUID.randomUUID());
        new CheckoutOperationRunner(checkouts, simulator).runNext();
        simulator.pay(checkoutOf(intentId), AMOUNT);
        intents.transition(intentId, PaymentStatus.CONFIRMED, null, UUID.randomUUID());
        intents.transition(intentId, PaymentStatus.REFUND_REQUESTED, "ORDER_CANCELLED", UUID.randomUUID());
        simulator.failNextRefund(RefundOutcome.ACCEPTED);
        new RefundRunner(refunds, simulator).runNext();

        requestLookup(intentId, "Reembolso demorando").andExpect(status().isAccepted());
        worker().runNext();
        assertThat(intentStatus(intentId)).isEqualTo("REFUND_REQUESTED");

        simulator.settleRefund(intentId);
        requestLookup(intentId, "Provedor avisou liquidação").andExpect(status().isAccepted());
        worker().runNext();

        assertThat(intentStatus(intentId)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM payment_external_operation WHERE intent_id = ? AND kind = 'REFUND'",
                        Integer.class,
                        intentId))
                .isEqualTo(1);
    }

    @Test
    void failedLookupIsAuditedAndChangesNothing() throws Exception {
        var intentId = unknownIntent();
        lookupsFail.set(true);

        requestLookup(intentId, "Tentar de novo").andExpect(status().isAccepted());
        worker().runNext();

        assertThat(intentStatus(intentId)).isEqualTo("UNKNOWN");
        mvc.perform(get("/api/v1/admin/payments/attention").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].intent.status").value("UNKNOWN"))
                .andExpect(jsonPath("$[0].operations[0].kind").value("QUERY"))
                .andExpect(jsonPath("$[0].operations[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].operations[0].result").value("LOOKUP_FAILED:IllegalStateException"))
                .andExpect(jsonPath("$[0].operations[0].requestedBy").value(ADMIN))
                .andExpect(jsonPath("$[0].operations[0].requestReason").value("Tentar de novo"));
    }

    @Test
    void onlyAdminsWithCsrfAReasonAndAnUncertainPaymentAreAccepted() throws Exception {
        var unknown = unknownIntent();
        var settled = intents.request(UUID.randomUUID(), AMOUNT, UUID.randomUUID());

        requestLookup(settled, "Sem motivo real")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PAYMENT_021"));
        requestLookup(UUID.randomUUID(), "Inexistente")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PAYMENT_020"));
        requestLookup(unknown, null).andExpect(status().isBadRequest());
        requestLookup(unknown, "  ").andExpect(jsonPath("$.codigo").value("PAYMENT_022"));
        mvc.perform(post("/api/v1/admin/payments/" + unknown + "/lookups")
                        .with(user("cliente@example.com").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/payments/" + unknown + "/lookups")
                        .with(user(ADMIN).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        assertThat(auditRows(unknown)).isZero();
    }
}
