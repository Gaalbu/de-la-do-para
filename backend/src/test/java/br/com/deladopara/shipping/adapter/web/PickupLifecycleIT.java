package br.com.deladopara.shipping.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import br.com.deladopara.orders.domain.OrderTransitions.InvalidOrderTransitionException;
import br.com.deladopara.shipping.application.PickupService;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class PickupLifecycleIT {

    private static final String TOKEN = "X-Order-Token";
    private static final String POINT = "Ponto de demonstração — Belém";
    private static final String WINDOW = "Segunda a sexta, 9h–18h (horário de Belém)";

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final OrderService orders;
    private final OrderAccessTokens tokens;
    private final AccountService accounts;
    private final PickupService pickups;
    private final ObjectMapper objectMapper;

    @Autowired
    PickupLifecycleIT(
            MockMvc mvc,
            JdbcTemplate jdbc,
            OrderService orders,
            OrderAccessTokens tokens,
            AccountService accounts,
            PickupService pickups,
            ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.orders = orders;
        this.tokens = tokens;
        this.accounts = accounts;
        this.pickups = pickups;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE shipping_pickup_code, purchase_order_access_token, purchase_order_status_history,"
                + " purchase_order_item, purchase_order, event_outbox CASCADE");
    }

    private UUID pickupOrder(String key) {
        return pickupOrder(key, null, "cliente@example.com");
    }

    private UUID pickupOrder(String key, UUID accountId, String email) {
        return pickupOrder(key, accountId, email, true);
    }

    private UUID pickupOrder(String key, UUID accountId, String email, boolean prepare) {
        var destination = objectMapper.createObjectNode().put("label", POINT).put("window", WINDOW);
        var id = orders.create(new CreateOrderCommand(
                        key,
                        accountId,
                        email,
                        FulfillmentMode.PICKUP,
                        4_500,
                        0,
                        null,
                        null,
                        0,
                        null,
                        4_500,
                        1,
                        null,
                        "pricing-v1",
                        destination,
                        List.of(new CreateOrderCommand.Item(UUID.randomUUID(), "Farinha", "500 g", 2, 2_250, 4_500)),
                        UUID.randomUUID()))
                .id();
        if (prepare) {
            orders.transition(id, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());
            orders.transition(id, OrderStatus.PREPARING, OrderActor.ADMIN, null, UUID.randomUUID());
        }
        return id;
    }

    private String ready(UUID id) throws Exception {
        mvc.perform(post("/api/v1/admin/orders/{id}/pickup/ready", id)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        return mvc.perform(get("/api/v1/orders/{id}/pickup", id).header(TOKEN, tokens.issue(id)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void onlyTheAuthorizedOrderOwnerCanSeeTheOneTimePickupCode() throws Exception {
        var id = pickupOrder("pickup-owner");
        var token = tokens.issue(id);
        var unauthorized = mvc.perform(get("/api/v1/orders/{id}/pickup", id).header(TOKEN, "wrong-order-token"))
                .andReturn();
        mvc.perform(get("/api/v1/orders/{id}/pickup", id)).andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/admin/orders/{id}/pickup/ready", id)
                        .with(user("customer@example.com").roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/admin/orders/{id}/pickup/ready", id)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        var response = mvc.perform(get("/api/v1/orders/{id}/pickup", id).header(TOKEN, token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.point").value(POINT))
                .andExpect(jsonPath("$.window").value(WINDOW))
                .andExpect(jsonPath("$.retentionBusinessDays").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var code = objectMapper.readTree(response).path("code").asText();
        assertThat(code).matches("[A-Z0-9]{10}");
        assertThat(unauthorized.getResponse().getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject(
                        "SELECT ciphertext FROM shipping_pickup_code WHERE order_id = ?", String.class, id))
                .doesNotContain(code);
        assertThat(jdbc.queryForObject(
                        "SELECT string_agg(payload::text, ' ') FROM event_outbox WHERE aggregate_id = ?",
                        String.class,
                        id.toString()))
                .doesNotContain(code);
        mvc.perform(get("/api/v1/admin/orders/{id}", id)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupCode").doesNotExist())
                .andExpect(jsonPath("$.destination.code").doesNotExist());
    }

    @Test
    void accountOwnerCanSeeTheCodeButAnotherAccountCannot() throws Exception {
        var ownerEmail = "pickup-owner-" + UUID.randomUUID() + "@example.com";
        var otherEmail = "pickup-other-" + UUID.randomUUID() + "@example.com";
        accounts.register(ownerEmail, "senha-forte-123", Account.Role.CUSTOMER);
        accounts.register(otherEmail, "senha-forte-123", Account.Role.CUSTOMER);
        var ownerId = accounts.accountIdByEmail(ownerEmail).orElseThrow();
        var id = pickupOrder("pickup-account", ownerId, ownerEmail);
        var token = tokens.issue(id);
        mvc.perform(post("/api/v1/admin/orders/{id}/pickup/ready", id)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/orders/{id}/pickup", id).with(user(ownerEmail).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNotEmpty());
        mvc.perform(get("/api/v1/orders/{id}/pickup", id).with(user(otherEmail).roles("CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders/{id}/pickup", id).header(TOKEN, token)).andExpect(status().isOk());
    }

    @Test
    void rejectsWrongCodeAndConsumesTheCorrectCodeOnlyOnce() throws Exception {
        var id = pickupOrder("pickup-confirm");
        var code = objectMapper.readTree(ready(id)).path("code").asText();
        var wrongCode = code.equals("AAAAAAAAAA") ? "BBBBBBBBBB" : "AAAAAAAAAA";

        mvc.perform(post("/api/v1/admin/orders/{id}/pickup/confirm", id)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(new ConfirmPickupRequest(wrongCode))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_order WHERE id = ?", String.class, id))
                .isEqualTo("READY_FOR_PICKUP");

        mvc.perform(post("/api/v1/admin/orders/{id}/pickup/confirm", id)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(new ConfirmPickupRequest(code))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/orders/{id}/pickup", id).header(TOKEN, tokens.issue(id)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/admin/orders/{id}/pickup/confirm", id)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(new ConfirmPickupRequest(code))))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_order WHERE id = ?", String.class, id))
                .isEqualTo("PICKED_UP");
        assertThat(jdbc.queryForObject(
                        "SELECT pickup_status FROM shipping_pickup_code WHERE order_id = ?", String.class, id))
                .isEqualTo("PICKED_UP");
        assertThat(jdbc.queryForObject(
                        "SELECT ciphertext FROM shipping_pickup_code WHERE order_id = ?", String.class, id))
                .isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM purchase_order_status_history WHERE order_id = ?", Integer.class, id))
                .isEqualTo(5);
    }

    @Test
    void doesNotReissueTheCodeWhenReadinessIsRepeated() throws Exception {
        var id = pickupOrder("pickup-repeat");
        mvc.perform(post("/api/v1/admin/orders/{id}/pickup/ready", id)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        var first = mvc.perform(get("/api/v1/orders/{id}/pickup", id).header(TOKEN, tokens.issue(id)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mvc.perform(post("/api/v1/admin/orders/{id}/pickup/ready", id)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict());
        var second = mvc.perform(get("/api/v1/orders/{id}/pickup", id).header(TOKEN, tokens.issue(id)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(second).path("code").asText())
                .isEqualTo(objectMapper.readTree(first).path("code").asText());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM shipping_pickup_code WHERE order_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void deliveryOrderCannotBeMarkedReadyForPickup() throws Exception {
        var destination = objectMapper.createObjectNode().put("label", "Belém").put("postalCode", "66000-000");
        var id = orders.create(new CreateOrderCommand(
                        "delivery-not-pickup",
                        null,
                        "cliente@example.com",
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
        orders.transition(id, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());
        orders.transition(id, OrderStatus.PREPARING, OrderActor.ADMIN, null, UUID.randomUUID());

        mvc.perform(post("/api/v1/admin/orders/{id}/pickup/ready", id)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shipping_pickup_code", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_order WHERE id = ?", String.class, id))
                .isEqualTo("PREPARING");
    }

    @Test
    void unpaidOrderCannotBeMarkedReadyForPickup() throws Exception {
        var id = pickupOrder("pickup-unpaid", null, "cliente@example.com", false);

        mvc.perform(post("/api/v1/admin/orders/{id}/pickup/ready", id)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shipping_pickup_code", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_order WHERE id = ?", String.class, id))
                .isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void cancellationAndPickupConfirmationCannotBothWin() throws Exception {
        var id = pickupOrder("pickup-cancel-race");
        var code = objectMapper.readTree(ready(id)).path("code").asText();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var pickup = executor.submit(() -> {
                start.await();
                try {
                    pickups.confirm(id, code, UUID.randomUUID());
                    return OrderStatus.PICKED_UP;
                } catch (PickupService.PickupUnavailableException e) {
                    return OrderStatus.CANCELLED;
                }
            });
            var cancel = executor.submit(() -> {
                start.await();
                try {
                    return orders.transition(
                            id, OrderStatus.CANCELLED, OrderActor.CUSTOMER, "CUSTOMER_REQUEST", UUID.randomUUID());
                } catch (InvalidOrderTransitionException e) {
                    return OrderStatus.PICKED_UP;
                }
            });
            start.countDown();
            var pickupWinner = pickup.get(10, TimeUnit.SECONDS);
            var cancelWinner = cancel.get(10, TimeUnit.SECONDS);

            assertThat(pickupWinner).isEqualTo(cancelWinner);
            assertThat(jdbc.queryForObject("SELECT status FROM purchase_order WHERE id = ?", String.class, id))
                    .isIn("PICKED_UP", "CANCELLED");
        }
    }

    public record ConfirmPickupRequest(String code) {}
}
