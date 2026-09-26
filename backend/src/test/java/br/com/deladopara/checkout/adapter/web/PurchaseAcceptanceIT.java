package br.com.deladopara.checkout.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.deladopara.support.PostgresTestContainer;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class PurchaseAcceptanceIT {

    private static final String KEY = "purchase-key-0001-abcdef";
    private static final String PICKUP = "PONTO-DEMO-BELEM";

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private Cookie sessionCookie;

    @Autowired
    PurchaseAcceptanceIT(MockMvc mvc, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void clean() {
        for (var trigger : new String[][] {
            {"purchase_order_item", "purchase_order_item_immutable"},
            {"purchase_order_status_history", "purchase_order_history_immutable"},
            {"purchase_order", "purchase_order_snapshot_guard"},
            {"purchase_order_access_token", "purchase_order_access_token_immutable"},
            {"payment_intent", "payment_intent_reference_guard"}
        }) {
            jdbc.execute("ALTER TABLE " + trigger[0] + " DISABLE TRIGGER " + trigger[1]);
        }
        jdbc.execute("TRUNCATE checkout_idempotency, payment_external_operation, payment_intent,"
                + " purchase_order_access_token, purchase_order_status_history, purchase_order_item, purchase_order,"
                + " inventory_reservation_line, inventory_reservation, inventory_movements, inventory_lots,"
                + " coupon_usage, coupon, checkout_snapshots, event_outbox CASCADE");
        jdbc.execute("DELETE FROM cart_items");
        jdbc.execute("DELETE FROM carts");
        for (var trigger : new String[][] {
            {"purchase_order_item", "purchase_order_item_immutable"},
            {"purchase_order_status_history", "purchase_order_history_immutable"},
            {"purchase_order", "purchase_order_snapshot_guard"},
            {"purchase_order_access_token", "purchase_order_access_token_immutable"},
            {"payment_intent", "payment_intent_reference_guard"}
        }) {
            jdbc.execute("ALTER TABLE " + trigger[0] + " ENABLE TRIGGER " + trigger[1]);
        }
        sessionCookie = null;
    }

    /** Sends the guest session cookie (Spring Session JDBC) and keeps the one the response sets. */
    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        if (sessionCookie != null) {
            request.cookie(sessionCookie);
        }
        var result = mvc.perform(request);
        var issued = result.andReturn().getResponse().getCookie("DLSESSION");
        if (issued != null) {
            sessionCookie = issued;
        }
        return result;
    }

    private UUID sku(long priceCents, int units) {
        var producer = UUID.randomUUID();
        var product = UUID.randomUUID();
        var sku = UUID.randomUUID();
        var suffix = sku.toString().substring(0, 8);
        jdbc.update(
                "INSERT INTO producers (id, slug, display_name, origin_label, description, created_at, updated_at)"
                        + " VALUES (?, ?, 'Produtor', 'Belém/PA', 'Demonstração', now(), now())",
                producer,
                "p-" + suffix);
        jdbc.update(
                "INSERT INTO products (id, slug, display_name, description, category, producer_id, created_at,"
                        + " updated_at) VALUES (?, ?, 'Farinha d''água', 'Demonstração', 'FOOD', ?, now(), now())",
                product,
                "farinha-" + suffix,
                producer);
        jdbc.update(
                "INSERT INTO product_skus (id, product_id, product_category, sku_code, sales_unit, net_content_grams,"
                        + " minimum_shelf_life_days, length_mm, width_mm, height_mm, gross_weight_grams,"
                        + " pickup_eligible, created_at, updated_at)"
                        + " VALUES (?, ?, 'FOOD', ?, 'pacote 500 g', 500, 30, 200, 140, 50, 520, true, now(), now())",
                sku,
                product,
                "SKU-" + suffix.toUpperCase());
        jdbc.update(
                "INSERT INTO pricing_sku_prices (sku_id, unit_price_cents, currency, updated_at)"
                        + " VALUES (?, ?, 'BRL', now())",
                sku,
                priceCents);
        jdbc.update(
                "INSERT INTO inventory_lots (id, sku_id, physical_units, expires_on, minimum_shelf_life_days,"
                        + " received_at, created_at, updated_at) VALUES (?, ?, ?, ?, 30, ?, now(), now())",
                UUID.randomUUID(),
                sku,
                units,
                LocalDate.now().plusDays(180),
                Timestamp.from(Instant.now()));
        return sku;
    }

    private void cart(long expectedVersion, String items) throws Exception {
        perform(put("/api/v1/cart/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":" + expectedVersion + ",\"items\":[" + items + "]}"))
                .andExpect(status().isOk());
    }

    private static String line(UUID sku, int quantity) {
        return "{\"skuId\":\"" + sku + "\",\"quantity\":" + quantity + "}";
    }

    private String[] snapshot() throws Exception {
        var body = perform(post("/api/v1/checkout/snapshots").with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new String[] {
            JsonPath.read(body, "$.snapshotId"), String.valueOf((Integer) JsonPath.read(body, "$.snapshotVersion"))
        };
    }

    private String summaryVersion(String[] snapshot) throws Exception {
        var body = perform(get("/api/v1/checkout/" + snapshot[0] + "/summary")
                        .param("snapshotVersion", snapshot[1])
                        .param("mode", "PICKUP")
                        .param("pickupOptionId", PICKUP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCents").value(3_600))
                .andExpect(jsonPath("$.lines[0].productName").value("Farinha d'água"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.summaryVersion");
    }

    private ResultActions purchase(String[] snapshot, String key, String email, String summaryVersion, String coupon)
            throws Exception {
        var couponField = coupon == null ? "" : ",\"couponCode\":\"" + coupon + "\"";
        var request = post("/api/v1/checkout/" + snapshot[0] + "/purchase")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"snapshotVersion\":" + snapshot[1] + ",\"mode\":\"PICKUP\",\"pickupOptionId\":\"" + PICKUP
                        + "\",\"email\":\"" + email + "\",\"summaryVersion\":\"" + summaryVersion + "\"" + couponField
                        + "}");
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return perform(request);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private void assertNothingWritten() {
        assertThat(count("purchase_order")).isZero();
        assertThat(count("inventory_reservation")).isZero();
        assertThat(count("payment_intent")).isZero();
        assertThat(count("checkout_idempotency")).isZero();
        assertThat(count("event_outbox")).isZero();
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(reserved_units), 0) FROM inventory_lots", Integer.class))
                .isZero();
    }

    @Test
    void guestPurchaseCreatesOrderReservationIntentAndEventsInOneTransaction() throws Exception {
        var farinha = sku(1_800, 5);
        cart(0, line(farinha, 2));
        var snapshot = snapshot();

        var body = purchase(snapshot, KEY, "Ana@Example.com", summaryVersion(snapshot), null)
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalCents").value(3_600))
                .andExpect(jsonPath("$.replayed").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String orderId = JsonPath.read(body, "$.orderId");
        String token = JsonPath.read(body, "$.accessToken");

        assertThat(token).isNotBlank();
        assertThat(jdbc.queryForMap(
                        "SELECT status, contact_email, total_cents FROM purchase_order WHERE id = ?::uuid", orderId))
                .containsEntry("status", "PENDING_PAYMENT")
                .containsEntry("contact_email", "ana@example.com")
                .containsEntry("total_cents", 3_600L);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM inventory_reservation WHERE reference = ?",
                        String.class,
                        "order:" + orderId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                        "SELECT reserved_units FROM inventory_lots WHERE sku_id = ?", Integer.class, farinha))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT amount_cents FROM payment_intent WHERE order_id = ?::uuid", Long.class, orderId))
                .isEqualTo(3_600L);
        assertThat(jdbc.queryForList(
                        "SELECT event_type FROM event_outbox WHERE status = 'PENDING' ORDER BY event_type",
                        String.class))
                .containsExactly("order.created", "payment.checkout_requested");
        assertThat(count("cart_items")).isZero();
        perform(get("/api/v1/orders/" + orderId).header("X-Order-Token", token)).andExpect(status().isOk());
    }

    @Test
    void replayReturnsTheSameOrderWithoutTokenAndChangedBodyConflicts() throws Exception {
        var farinha = sku(1_800, 5);
        cart(0, line(farinha, 2));
        var snapshot = snapshot();
        var version = summaryVersion(snapshot);
        var first = purchase(snapshot, KEY, "ana@example.com", version, null)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        purchase(snapshot, KEY, "ana@example.com", version, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value((String) JsonPath.read(first, "$.orderId")))
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        purchase(snapshot, KEY, "bia@example.com", version, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CHECKOUT_011"));

        assertThat(count("purchase_order")).isEqualTo(1);
        assertThat(count("payment_intent")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT reserved_units FROM inventory_lots WHERE sku_id = ?", Integer.class, farinha))
                .isEqualTo(2);
    }

    @Test
    void changedPriceAfterSummaryIsRejectedWithTheNewSummaryAndNoWrites() throws Exception {
        var farinha = sku(1_800, 5);
        cart(0, line(farinha, 2));
        var snapshot = snapshot();
        var version = summaryVersion(snapshot);
        jdbc.update("UPDATE pricing_sku_prices SET unit_price_cents = 1900 WHERE sku_id = ?", farinha);

        purchase(snapshot, KEY, "ana@example.com", version, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CHECKOUT_012"))
                .andExpect(jsonPath("$.summary.totalCents").value(3_800));

        assertNothingWritten();
    }

    @Test
    void insufficientStockRollsEverythingBack() throws Exception {
        var farinha = sku(1_800, 1);
        cart(0, line(farinha, 2));
        var snapshot = snapshot();

        purchase(snapshot, KEY, "ana@example.com", summaryVersion(snapshot), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CHECKOUT_013"));

        assertNothingWritten();
        assertThat(count("cart_items")).isEqualTo(1);
    }

    @Test
    void guestCannotUseCouponWithoutVerifiedEmail() throws Exception {
        var farinha = sku(1_800, 5);
        jdbc.update("INSERT INTO coupon (id, code_normalized, discount_type, discount_value, valid_from, valid_until)"
                + " VALUES (gen_random_uuid(), 'BEMVINDO', 'PERCENTAGE', 10, now() - interval '1 day',"
                + " now() + interval '1 day')");
        cart(0, line(farinha, 2));
        var snapshot = snapshot();
        var body = perform(get("/api/v1/checkout/" + snapshot[0] + "/summary")
                        .param("snapshotVersion", snapshot[1])
                        .param("mode", "PICKUP")
                        .param("pickupOptionId", PICKUP)
                        .param("couponCode", "bemvindo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountCents").value(360))
                .andReturn()
                .getResponse()
                .getContentAsString();

        purchase(snapshot, KEY, "ana@example.com", JsonPath.read(body, "$.summaryVersion"), "bemvindo")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CHECKOUT_014"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("EMAIL_NOT_VERIFIED")));

        assertNothingWritten();
    }

    @Test
    void itemsAddedAfterTheSnapshotStayInTheCart() throws Exception {
        var farinha = sku(1_800, 5);
        var castanha = sku(2_800, 5);
        cart(0, line(farinha, 2));
        var snapshot = snapshot();
        var version = summaryVersion(snapshot);
        var current = perform(get("/api/v1/cart")).andReturn().getResponse().getContentAsString();
        cart(((Number) JsonPath.read(current, "$.version")).longValue(), line(farinha, 3) + "," + line(castanha, 1));

        purchase(snapshot, KEY, "ana@example.com", version, null).andExpect(status().isCreated());

        assertThat(jdbc.queryForList(
                        "SELECT sku_id::text || ':' || quantity FROM cart_items ORDER BY sku_id", String.class))
                .containsExactlyInAnyOrder(farinha + ":1", castanha + ":1");
    }

    @Test
    void missingOrMalformedIdempotencyKeyIsRejected() throws Exception {
        var farinha = sku(1_800, 5);
        cart(0, line(farinha, 1));
        var snapshot = snapshot();

        purchase(snapshot, null, "ana@example.com", "x", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CHECKOUT_015"));
        purchase(snapshot, "curta", "ana@example.com", "x", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CHECKOUT_015"));
        assertNothingWritten();
    }
}
