package br.com.deladopara.payments.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.deladopara.payments.application.ProviderEventInbox;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "payments.asaas.webhook-token=" + AsaasWebhookIT.TOKEN)
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class AsaasWebhookIT {

    static final String TOKEN = "token-de-teste-do-webhook-asaas-0123456789";
    private static final String PAID = """
            {"id":"%s","event":"%s","dateCreated":"2026-09-25 10:00:00",
             "checkout":{"id":"chk_1","status":"PAID","customer":"cus_x","customerData":{"name":"Ana"}}}
            """;

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final ProviderEventInbox inbox;
    private final ObjectMapper objectMapper;

    @Autowired
    AsaasWebhookIT(MockMvc mvc, JdbcTemplate jdbc, ProviderEventInbox inbox, ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.inbox = inbox;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE payment_provider_event");
    }

    private ResultActions send(String token, String body) throws Exception {
        var request = post("/api/v1/webhooks/asaas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (token != null) {
            request.header("asaas-access-token", token);
        }
        return mvc.perform(request);
    }

    private int stored() {
        return jdbc.queryForObject("SELECT count(*) FROM payment_provider_event", Integer.class);
    }

    @Test
    void authenticatedEventIsStoredBeforeTheAcknowledgementWithoutCustomerData() throws Exception {
        send(TOKEN, PAID.formatted("evt_1", "CHECKOUT_PAID")).andExpect(status().isOk());

        assertThat(jdbc.queryForMap("SELECT * FROM payment_provider_event"))
                .containsEntry("event_id", "evt_1")
                .containsEntry("event_type", "CHECKOUT_PAID")
                .containsEntry("checkout_id", "chk_1")
                .containsEntry("checkout_status", "PAID")
                .containsEntry("status", "RECEIVED")
                .doesNotContainValue("Ana");
    }

    @Test
    void redeliveryIsAcknowledgedAndStoredOnce() throws Exception {
        send(TOKEN, PAID.formatted("evt_1", "CHECKOUT_PAID")).andExpect(status().isOk());
        send(TOKEN, PAID.formatted("evt_1", "CHECKOUT_PAID")).andExpect(status().isOk());

        assertThat(stored()).isEqualTo(1);
    }

    @Test
    void missingOrWrongTokenIsRejectedAndNothingStored() throws Exception {
        send(null, PAID.formatted("evt_1", "CHECKOUT_PAID"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("PAYMENT_010"));
        send(TOKEN + "x", PAID.formatted("evt_1", "CHECKOUT_PAID")).andExpect(status().isUnauthorized());

        assertThat(stored()).isZero();
    }

    @Test
    void invalidPayloadIsRejected() throws Exception {
        send(TOKEN, "{\"event\":\"CHECKOUT_PAID\"}").andExpect(status().isBadRequest());
        send(TOKEN, "{\"id\":\"evt_2\",\"event\":\"CHECKOUT_PAID\",\"checkout\":{}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PAYMENT_012"));
        send(TOKEN, "não é json").andExpect(status().isBadRequest());

        assertThat(stored()).isZero();
    }

    @Test
    void unhandledEventTypesAreAcknowledgedAsIgnored() throws Exception {
        send(TOKEN, PAID.formatted("evt_3", "CHECKOUT_CREATED")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT status FROM payment_provider_event", String.class))
                .isEqualTo("IGNORED");
    }

    @Test
    void oversizedBodyIsRejected() throws Exception {
        var padding = "x".repeat(AsaasWebhookController.MAX_BODY_BYTES);
        send(TOKEN, "{\"id\":\"evt_4\",\"event\":\"CHECKOUT_PAID\",\"pad\":\"" + padding + "\"}")
                .andExpect(status().isContentTooLarge());
        assertThat(stored()).isZero();
    }

    @Test
    void databaseFailureIsNeverAcknowledged() throws Exception {
        jdbc.execute(
                "ALTER TABLE payment_provider_event ADD CONSTRAINT webhook_it_fail CHECK (event_id <> 'evt_fail')");
        try {
            var status = send(TOKEN, PAID.formatted("evt_fail", "CHECKOUT_PAID"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            assertThat(HttpStatus.valueOf(status).is2xxSuccessful()).isFalse();
        } catch (jakarta.servlet.ServletException propagated) {
            assertThat(propagated).hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
        } finally {
            jdbc.execute("ALTER TABLE payment_provider_event DROP CONSTRAINT webhook_it_fail");
        }
        assertThat(stored()).isZero();
    }

    @Test
    void unconfiguredTokenRejectsEveryCall() {
        var controller = new AsaasWebhookController(inbox, objectMapper, "");

        var response = controller.receive(
                TOKEN, PAID.formatted("evt_5", "CHECKOUT_PAID").getBytes(StandardCharsets.UTF_8));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
