package br.com.deladopara.shipping.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.deladopara.orders.application.OrderAccessTokens;
import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.orders.domain.OrderStatus;
import br.com.deladopara.shipping.application.ShipmentTrackingService;
import br.com.deladopara.support.PostgresTestContainer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "shipping.melhor-envio.webhook-secret=local-test-secret")
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class ShipmentTrackingServiceIT {

    private static final String WEBHOOK_SECRET = "local-test-secret";
    private static final Instant CREATED_AT = Instant.parse("2026-10-05T12:00:00Z");
    private final ShipmentTrackingService tracking;
    private final OrderAccessTokens accessTokens;
    private final JdbcTemplate jdbc;
    private final MockMvc mockMvc;

    @Autowired
    ShipmentTrackingServiceIT(
            ShipmentTrackingService tracking, OrderAccessTokens accessTokens, JdbcTemplate jdbc, MockMvc mockMvc) {
        this.tracking = tracking;
        this.accessTokens = accessTokens;
        this.jdbc = jdbc;
        this.mockMvc = mockMvc;
    }

    @Test
    @Transactional
    void storesTrackingFactsWithTheGeneratedLabelPackageMapping() {
        var label = generatedLabel();
        var body = body("posted");

        assertThat(tracking.record(label.providerId, "order.posted", CREATED_AT, body))
                .isTrue();
        var stored = jdbc.queryForMap(
                "SELECT order_id, shipment_unit_id, package_sequences, progress, event_type"
                        + " FROM shipping_package_tracking WHERE provider_shipment_id = ?",
                label.providerId);

        assertThat(stored.get("order_id")).isEqualTo(label.orderId);
        assertThat(stored.get("shipment_unit_id")).isEqualTo(label.unitId);
        assertThat(stored.get("package_sequences").toString()).isEqualTo("[1, 2]");
        assertThat(stored.get("progress")).isEqualTo("IN_TRANSIT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shipping_tracking_event", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @Transactional
    void deduplicatesAnExactProviderRedelivery() {
        var label = generatedLabel();
        var body = body("same-event");

        assertThat(tracking.record(label.providerId, "order.posted", CREATED_AT, body))
                .isTrue();
        assertThat(tracking.record(label.providerId, "order.posted", CREATED_AT, body))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shipping_tracking_event", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @Transactional
    void ignoresOutOfOrderStatusWithoutRegressingStoredProgress() {
        var label = generatedLabel();
        tracking.record(label.providerId, "order.delivered", CREATED_AT.plusSeconds(30), body("delivered"));

        tracking.record(label.providerId, "order.posted", CREATED_AT.plusSeconds(60), body("late-posted"));

        assertThat(jdbc.queryForObject(
                        "SELECT progress FROM shipping_package_tracking WHERE provider_shipment_id = ?",
                        String.class,
                        label.providerId))
                .isEqualTo("DELIVERED");
    }

    @Test
    @Transactional
    void recordsQueriedProviderStatusAtTheLocalObservationTime() {
        var label = generatedLabel();
        var observedAt = CREATED_AT.plusSeconds(90);

        tracking.recordProviderStatus(label.providerId, "delivered", observedAt, body("query-snapshot"));

        var row = jdbc.queryForMap(
                "SELECT progress, occurred_at FROM shipping_package_tracking WHERE provider_shipment_id = ?",
                label.providerId);
        assertThat(row.get("progress")).isEqualTo("DELIVERED");
        assertThat(((Timestamp) row.get("occurred_at")).toInstant()).isEqualTo(observedAt);
    }

    @Test
    @Transactional
    void exposesAuthorizedPackageProgressWithoutProviderIdentifiers() throws Exception {
        var label = generatedLabel();
        tracking.record(label.providerId, "order.posted", CREATED_AT, body("posted-with-secret-code"));
        var token = accessTokens.issue(label.orderId);

        mockMvc.perform(get("/api/v1/orders/{id}/shipping-progress", label.orderId)
                        .header("X-Order-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfillmentMode").value("DELIVERY"))
                .andExpect(jsonPath("$.shipments[0].packageSequences[0]").value(1))
                .andExpect(jsonPath("$.shipments[0].packageSequences[1]").value(2))
                .andExpect(jsonPath("$.shipments[0].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.shipments[0].exception").value(false))
                .andExpect(jsonPath("$.shipments[0].providerShipmentId").doesNotExist())
                .andExpect(result -> {
                    assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
                    assertThat(result.getResponse().getContentAsString()).doesNotContain(label.providerId);
                    assertThat(result.getResponse().getContentAsString()).doesNotContain("posted-with-secret-code");
                });
    }

    @Test
    @Transactional
    void keepsPartialDeliveryVisibleAsSeparatePackageGroups() throws Exception {
        var first = generatedLabel();
        var second = generatedLabelForOrder(first.orderId, "[3]");
        tracking.record(first.providerId, "order.delivered", CREATED_AT, body("first-delivered"));
        tracking.record(second.providerId, "order.posted", CREATED_AT, body("second-posted"));
        jdbc.update("UPDATE purchase_order SET status = ? WHERE id = ?", OrderStatus.DELIVERED.name(), first.orderId);
        var token = accessTokens.issue(first.orderId);

        mockMvc.perform(get("/api/v1/orders/{id}/shipping-progress", first.orderId)
                        .header("X-Order-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipments.length()").value(2))
                .andExpect(jsonPath("$.shipments[0].packageSequences[0]").value(1))
                .andExpect(jsonPath("$.shipments[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.shipments[1].packageSequences[0]").value(3))
                .andExpect(jsonPath("$.shipments[1].status").value("IN_TRANSIT"));
    }

    @Test
    @Transactional
    void keepsConflictingProviderIdsForOnePackageGroupUnderReview() throws Exception {
        var delivered = generatedLabel();
        tracking.record(delivered.providerId, "order.delivered", CREATED_AT, body("delivered-id"));
        jdbc.update(
                "INSERT INTO shipping_package_tracking (provider_shipment_id, order_id, shipment_unit_id,"
                        + " package_sequences, event_type, progress, exception, occurred_at)"
                        + " VALUES (?, ?, ?, '[1,2]'::jsonb, 'order.posted', 'IN_TRANSIT', false, ?)",
                "shipment-conflicting-" + UUID.randomUUID(),
                delivered.orderId,
                delivered.unitId,
                Timestamp.from(CREATED_AT.plusSeconds(1)));
        var token = accessTokens.issue(delivered.orderId);

        mockMvc.perform(get("/api/v1/orders/{id}/shipping-progress", delivered.orderId)
                        .header("X-Order-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipments.length()").value(1))
                .andExpect(jsonPath("$.shipments[0].status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.shipments[0].exception").value(true));
    }

    @Test
    void doesNotExposePackageProgressToAnAnonymousCaller() throws Exception {
        var label = generatedLabel();

        mockMvc.perform(get("/api/v1/orders/{id}/shipping-progress", label.orderId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownShipmentIdsAndRollsBackTheEventLedgerInsert() {
        assertThatThrownBy(() -> tracking.record("unmapped-id", "order.posted", CREATED_AT, body("unknown")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("shipment tracking id is not associated with a generated label");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM shipping_tracking_event", Integer.class))
                .isZero();
    }

    @Test
    void acceptsSignedWebhookWithoutSessionOrCsrfToken() throws Exception {
        var label = generatedLabel();
        var body = ("{\"event\":\"order.posted\",\"data\":{\"id\":\"" + label.providerId
                        + "\",\"posted_at\":\"2026-10-05T12:00:00Z\"}}")
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/webhooks/melhor-envio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-ME-Signature", signature(body))
                        .content(body))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT progress FROM shipping_package_tracking WHERE provider_shipment_id = ?",
                        String.class,
                        label.providerId))
                .isEqualTo("IN_TRANSIT");
    }

    @Test
    void deniesAnUnsignedAnonymousWebhook() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/melhor-envio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private GeneratedLabel generatedLabel() {
        var orderId = UUID.randomUUID();
        insertOrder(orderId);
        return generatedLabelForOrder(orderId, "[1,2]");
    }

    private GeneratedLabel generatedLabelForOrder(UUID orderId, String packages) {
        return generatedLabelForOrder(orderId, packages, UUID.randomUUID());
    }

    private GeneratedLabel generatedLabelForOrder(UUID orderId, String packages, UUID unitId) {
        var providerId = "shipment-" + UUID.randomUUID();
        var operationId = UUID.randomUUID();
        var correlationId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO shipping_label_operation (id, order_id, shipment_unit_id, package_sequences,"
                        + " step, state, correlation_id, provider_shipment_id, created_at, updated_at, requested_at,"
                        + " resolved_at) VALUES (?, ?, ?, ?::jsonb, 'GENERATE', 'SUCCEEDED', ?, ?, ?, ?, ?, ?)",
                operationId,
                orderId,
                unitId,
                packages,
                correlationId,
                providerId,
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT.plusSeconds(3)),
                Timestamp.from(CREATED_AT.plusSeconds(1)),
                Timestamp.from(CREATED_AT.plusSeconds(3)));
        return new GeneratedLabel(orderId, unitId, providerId);
    }

    private void insertOrder(UUID orderId) {
        jdbc.update(
                "INSERT INTO purchase_order (id, checkout_key, contact_email, fulfillment_mode, currency,"
                        + " subtotal_cents, shipping_cents, discount_cents, total_cents, preparation_days,"
                        + " pricing_rule_version, destination, status, status_sequence, created_at, updated_at)"
                        + " VALUES (?, ?, 'tracking@example.com', ?, 'BRL', 1000, 0, 0, 1000, 0,"
                        + " 'test-v1', '{}'::jsonb, ?, 1, ?, ?)",
                orderId,
                "tracking-" + UUID.randomUUID(),
                FulfillmentMode.DELIVERY.name(),
                OrderStatus.PAID.name(),
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT));
    }

    private static byte[] body(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String signature(byte[] body) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }

    private record GeneratedLabel(UUID orderId, UUID unitId, String providerId) {}
}
