package br.com.deladopara.orders.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class OrderAccessIT {

    private static final String TOKEN = "X-Order-Token";

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final OrderService orders;
    private final OrderAccessTokens tokens;
    private final AccountService accounts;
    private final ObjectMapper objectMapper;

    @Autowired
    OrderAccessIT(
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

    private String register() {
        var email = "cliente-" + UUID.randomUUID() + "@example.com";
        accounts.register(email, "senha-forte-123", Account.Role.CUSTOMER);
        return email;
    }

    private UUID accountId(String email) {
        return accounts.accountIdByEmail(email).orElseThrow();
    }

    private UUID order(String key, String email, UUID accountId) {
        var destination = objectMapper.createObjectNode().put("label", "Ponto de demonstração — Belém");
        return orders.create(new CreateOrderCommand(
                        key,
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
                        destination,
                        List.of(new CreateOrderCommand.Item(UUID.randomUUID(), "Farinha", "500 g", 2, 2_250, 4_500)),
                        UUID.randomUUID()))
                .id();
    }

    @Test
    void guestReadsOnlyTheOrderTheTokenBelongsTo() throws Exception {
        var mine = order("g-1", "convidado@example.com", null);
        var other = order("g-2", "outro@example.com", null);
        var token = tokens.issue(mine);
        orders.transition(mine, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());

        mvc.perform(get("/api/v1/orders/" + mine).header(TOKEN, token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(jsonPath("$.id").value(mine.toString()))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalCents").value(5_250))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productName").value("Farinha"))
                .andExpect(jsonPath("$.destination.label").value("Ponto de demonstração — Belém"))
                .andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.history[1].to").value("PAID"));
        mvc.perform(get("/api/v1/orders/" + other).header(TOKEN, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("ORDER_003"));
    }

    @Test
    void guestWithoutOrWithWrongTokenIsRejectedWithoutRevealingTheOrder() throws Exception {
        var id = order("g-3", "convidado@example.com", null);
        tokens.issue(id);

        mvc.perform(get("/api/v1/orders/" + id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("ORDER_003"));
        mvc.perform(get("/api/v1/orders/" + id).header(TOKEN, "token-errado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("ORDER_003"));
        mvc.perform(get("/api/v1/orders/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenIsStoredOnlyAsHash() {
        var id = order("g-4", "convidado@example.com", null);

        var token = tokens.issue(id);

        var stored = jdbc.queryForList("SELECT token_hash FROM purchase_order_access_token", String.class);
        assertThat(stored).hasSize(1).doesNotContain(token);
        assertThat(stored.get(0)).hasSize(64);
    }

    @Test
    void customerSeesOwnOrdersAndForeignOrderIsNotFound() throws Exception {
        var ana = register();
        var bia = register();
        var anaOrder = order("c-1", ana, accountId(ana));
        var anaSecond = order("c-2", ana, accountId(ana));
        var biaOrder = order("c-3", bia, accountId(bia));

        mvc.perform(get("/api/v1/orders/" + anaOrder).with(user(ana).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(anaOrder.toString()));
        mvc.perform(get("/api/v1/orders/" + biaOrder).with(user(ana).roles("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ORDER_001"));
        mvc.perform(get("/api/v1/orders/" + UUID.randomUUID()).with(user(ana).roles("CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders").with(user(ana).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(anaSecond.toString()))
                .andExpect(jsonPath("$.content[0].itemCount").value(1));
        mvc.perform(get("/api/v1/orders?page=1&size=1").with(user(ana).roles("CUSTOMER")))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(anaOrder.toString()));
    }

    @Test
    void guestOrderIsNeverLinkedToAnAccountByEmailEquality() throws Exception {
        var ana = register();
        var guestOrder = order("c-4", ana, null);

        mvc.perform(get("/api/v1/orders/" + guestOrder).with(user(ana).roles("CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders").with(user(ana).roles("CUSTOMER")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void adminReadsAnyOrderAndPagedListOthersAreDenied() throws Exception {
        var ana = register();
        var id = order("a-1", ana, accountId(ana));
        order("a-2", "convidado@example.com", null);

        mvc.perform(get("/api/v1/admin/orders/" + id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value(ana));
        mvc.perform(get("/api/v1/admin/orders").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/admin/orders/" + UUID.randomUUID())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/orders").with(user(ana).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        var ana = register();

        mvc.perform(get("/api/v1/orders?size=0").with(user(ana).roles("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ORDER_004"));
        mvc.perform(get("/api/v1/orders?page=-1").with(user(ana).roles("CUSTOMER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/orders?size=abc").with(user(ana).roles("CUSTOMER")))
                .andExpect(status().isBadRequest());
    }
}
