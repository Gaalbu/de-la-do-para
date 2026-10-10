package br.com.deladopara.shipping.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private final JdbcTemplate jdbc;
    private final MockMvc mockMvc;

    @Autowired
    ShipmentTrackingServiceIT(ShipmentTrackingService tracking, JdbcTemplate jdbc, MockMvc mockMvc) {
        this.tracking = tracking;
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
        var unitId = UUID.randomUUID();
        var providerId = "shipment-" + UUID.randomUUID();
        var operationId = UUID.randomUUID();
        var correlationId = UUID.randomUUID();
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
        jdbc.update(
                "INSERT INTO shipping_label_operation (id, order_id, shipment_unit_id, package_sequences,"
                        + " step, state, correlation_id, provider_shipment_id, created_at, updated_at, requested_at,"
                        + " resolved_at) VALUES (?, ?, ?, '[1,2]'::jsonb, 'GENERATE', 'SUCCEEDED', ?, ?, ?, ?, ?, ?)",
                operationId,
                orderId,
                unitId,
                correlationId,
                providerId,
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT.plusSeconds(3)),
                Timestamp.from(CREATED_AT.plusSeconds(1)),
                Timestamp.from(CREATED_AT.plusSeconds(3)));
        return new GeneratedLabel(orderId, unitId, providerId);
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
