package br.com.deladopara.checkout.adapter.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.deladopara.identity.application.AccountService;
import br.com.deladopara.identity.domain.Account;
import br.com.deladopara.orders.application.CreateOrderCommand;
import br.com.deladopara.orders.application.OrderAccessTokens;
import br.com.deladopara.orders.application.OrderService;
import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** C68: who may ask for a cancellation and what the HTTP answer says; the coordinator rules are in OrderCancellationIT. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class CancellationApiIT {

    private static final String TOKEN = "X-Order-Token";

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final OrderService orders;
    private final OrderAccessTokens tokens;
    private final AccountService accounts;
    private final ObjectMapper objectMapper;

    @Autowired
    CancellationApiIT(
            MockMvc mvc,
            JdbcTemplate jdbc,
            OrderService orders,
            OrderAccessTokens tokens,
            AccountService accounts,
            ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.orders = orders;
        this.tokens = tokens;
        this.accounts = accounts;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE purchase_order_access_token, purchase_order_status_history, purchase_order_item,"
                + " purchase_order, event_outbox CASCADE");
    }

    /** A delivery order already handed to the carrier: a cancellation request sends it to review (D30). */
    private UUID inTransit(UUID accountId, String email) {
        var id = orders.create(new CreateOrderCommand(
                        UUID.randomUUID().toString(),
                        accountId,
                        email,
                        FulfillmentMode.DELIVERY,
                        4_500,
                        750,
                        null,
                        null,
                        0,
                        null,
                        5_250,
                        1,
                        5,
                        "pricing-v1",
                        objectMapper.createObjectNode().put("label", "Belém"),
                        List.of(new CreateOrderCommand.Item(UUID.randomUUID(), "Farinha", "500 g", 2, 2_250, 4_500)),
                        UUID.randomUUID()))
                .id();
        orders.transition(id, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());
        orders.transition(id, OrderStatus.PREPARING, OrderActor.ADMIN, null, UUID.randomUUID());
        orders.transition(id, OrderStatus.IN_TRANSIT, OrderActor.SYSTEM, "HANDED_OFF", UUID.randomUUID());
        return id;
    }

    private String customer() {
        var email = "cliente-" + UUID.randomUUID() + "@example.com";
        accounts.register(email, "senha-forte-123", Account.Role.CUSTOMER);
        return email;
    }

    @Test
    void guestWithTheOrderTokenGetsTheResultingStatusAndRepeatingKeepsIt() throws Exception {
        var id = inTransit(null, "convidado@example.com");
        var token = tokens.issue(id);

        for (var attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/orders/" + id + "/cancellation")
                            .header(TOKEN, token)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        }
    }

    @Test
    void requestWithoutProofOrWithAnotherOrdersTokenIsRefused() throws Exception {
        var id = inTransit(null, "convidado@example.com");
        var other = inTransit(null, "outro@example.com");

        mvc.perform(post("/api/v1/orders/" + id + "/cancellation").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("ORDER_003"));
        mvc.perform(post("/api/v1/orders/" + id + "/cancellation")
                        .header(TOKEN, tokens.issue(other))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutCsrfNothingChanges() throws Exception {
        var id = inTransit(null, "convidado@example.com");

        mvc.perform(post("/api/v1/orders/" + id + "/cancellation").header(TOKEN, tokens.issue(id)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCancelsButAnotherCustomerSeesNoOrder() throws Exception {
        var owner = customer();
        var id = inTransit(accounts.accountIdByEmail(owner).orElseThrow(), owner);

        mvc.perform(post("/api/v1/orders/" + id + "/cancellation")
                        .with(user(customer()).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ORDER_001"));
        mvc.perform(post("/api/v1/orders/" + id + "/cancellation")
                        .with(user(owner).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    void finishedOrderIsAConflictAndOnlyAdminsUseTheAdminRoute() throws Exception {
        var id = inTransit(null, "convidado@example.com");
        orders.transition(id, OrderStatus.DELIVERED, OrderActor.SYSTEM, "DELIVERED", UUID.randomUUID());

        mvc.perform(post("/api/v1/admin/orders/" + id + "/cancellation")
                        .with(user(customer()).roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/orders/" + id + "/cancellation")
                        .with(user("admin@deladopara.local").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ORDER_002"));
    }
}
