package br.com.deladopara.orders.adapter.persistence;

import br.com.deladopara.orders.application.CreateOrderCommand;
import br.com.deladopara.orders.application.OrderView;
import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OrderRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<OrderView> view(UUID id) {
        return jdbc
                .query(
                        "SELECT * FROM purchase_order WHERE id = ?",
                        (rs, row) -> new OrderView(
                                id,
                                OrderStatus.valueOf(rs.getString("status")),
                                FulfillmentMode.valueOf(rs.getString("fulfillment_mode")),
                                rs.getString("contact_email"),
                                rs.getString("currency"),
                                rs.getLong("subtotal_cents"),
                                rs.getLong("shipping_cents"),
                                rs.getLong("discount_cents"),
                                rs.getString("coupon_code"),
                                rs.getLong("total_cents"),
                                rs.getInt("preparation_days"),
                                (Integer) rs.getObject("delivery_days"),
                                json(rs.getString("destination")),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("updated_at").toInstant(),
                                items(id),
                                history(id).stream()
                                        .map(h -> new OrderView.Transition(
                                                h.sequence(), h.from(), h.to(), h.actor(), h.reason(), h.occurredAt()))
                                        .toList()),
                        id)
                .stream()
                .findFirst();
    }

    public boolean ownedBy(UUID id, UUID accountId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM purchase_order WHERE id = ? AND account_id = ?)",
                Boolean.class,
                id,
                accountId));
    }

    public List<OrderView.Summary> summariesForAccount(UUID accountId, int limit, long offset) {
        return jdbc.query(
                SUMMARY + " WHERE o.account_id = ? ORDER BY o.created_at DESC, o.id LIMIT ? OFFSET ?",
                OrderRepository::summary,
                accountId,
                limit,
                offset);
    }

    public long countForAccount(UUID accountId) {
        return jdbc.queryForObject("SELECT count(*) FROM purchase_order WHERE account_id = ?", Long.class, accountId);
    }

    public List<OrderView.Summary> summariesForAdmin(int limit, long offset) {
        return jdbc.query(
                SUMMARY + " ORDER BY o.created_at DESC, o.id LIMIT ? OFFSET ?",
                OrderRepository::summary,
                limit,
                offset);
    }

    public long countAll() {
        return jdbc.queryForObject("SELECT count(*) FROM purchase_order", Long.class);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertAccessToken(String tokenHash, UUID orderId, Instant now) {
        jdbc.update(
                "INSERT INTO purchase_order_access_token (token_hash, order_id, created_at) VALUES (?, ?, ?)",
                tokenHash,
                orderId,
                Timestamp.from(now));
    }

    public boolean accessTokenGrants(String tokenHash, UUID orderId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM purchase_order_access_token WHERE token_hash = ? AND order_id = ?)",
                Boolean.class,
                tokenHash,
                orderId));
    }

    private List<OrderView.Item> items(UUID id) {
        return jdbc.query(
                "SELECT * FROM purchase_order_item WHERE order_id = ? ORDER BY line_no",
                (rs, row) -> new OrderView.Item(
                        rs.getString("product_name"),
                        rs.getString("sku_label"),
                        rs.getInt("quantity"),
                        rs.getLong("unit_price_cents"),
                        rs.getLong("line_total_cents")),
                id);
    }

    private Map<String, Object> json(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored order destination is not valid JSON", e);
        }
    }

    private static final String SUMMARY = "SELECT o.id, o.status, o.fulfillment_mode, o.total_cents, o.created_at,"
            + " (SELECT count(*) FROM purchase_order_item i WHERE i.order_id = o.id) AS item_count"
            + " FROM purchase_order o";

    private static OrderView.Summary summary(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new OrderView.Summary(
                rs.getObject("id", UUID.class),
                OrderStatus.valueOf(rs.getString("status")),
                FulfillmentMode.valueOf(rs.getString("fulfillment_mode")),
                rs.getLong("total_cents"),
                rs.getInt("item_count"),
                rs.getTimestamp("created_at").toInstant());
    }

    public Optional<OrderHead> findByCheckoutKey(String checkoutKey) {
        return jdbc
                .query("SELECT * FROM purchase_order WHERE checkout_key = ?", OrderRepository::head, checkoutKey)
                .stream()
                .findFirst();
    }

    public Optional<OrderHead> find(UUID id) {
        return jdbc.query("SELECT * FROM purchase_order WHERE id = ?", OrderRepository::head, id).stream()
                .findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<OrderHead> lock(UUID id) {
        return jdbc.query("SELECT * FROM purchase_order WHERE id = ? FOR UPDATE", OrderRepository::head, id).stream()
                .findFirst();
    }

    public List<HistoryRow> history(UUID id) {
        return jdbc.query(
                "SELECT * FROM purchase_order_status_history WHERE order_id = ? ORDER BY sequence",
                (rs, row) -> new HistoryRow(
                        rs.getInt("sequence"),
                        rs.getString("from_status") == null ? null : OrderStatus.valueOf(rs.getString("from_status")),
                        OrderStatus.valueOf(rs.getString("to_status")),
                        OrderActor.valueOf(rs.getString("actor")),
                        rs.getString("reason"),
                        rs.getTimestamp("occurred_at").toInstant()),
                id);
    }

    public int itemCount(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM purchase_order_item WHERE order_id = ?", Integer.class, id);
    }

    /** Returns false when the checkout key already exists (idempotent creation). */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean insert(UUID id, CreateOrderCommand c, String destinationJson, Instant now) {
        var inserted = jdbc.update(
                """
                INSERT INTO purchase_order
                    (id, checkout_key, account_id, contact_email, fulfillment_mode, currency, subtotal_cents,
                     shipping_cents, discount_type, discount_value, discount_cents, coupon_code, total_cents,
                     preparation_days, delivery_days, pricing_rule_version, destination, status, status_sequence,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'BRL', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'PENDING_PAYMENT', 0, ?, ?)
                ON CONFLICT (checkout_key) DO NOTHING
                """,
                id,
                c.checkoutKey(),
                c.accountId(),
                c.contactEmail(),
                c.mode().name(),
                c.subtotalCents(),
                c.shippingCents(),
                c.discountType(),
                c.discountValue(),
                c.discountCents(),
                c.couponCode(),
                c.totalCents(),
                c.preparationDays(),
                c.deliveryDays(),
                c.pricingRuleVersion(),
                destinationJson,
                Timestamp.from(now),
                Timestamp.from(now));
        if (inserted == 0) {
            return false;
        }
        var line = 0;
        for (var item : c.items()) {
            jdbc.update(
                    "INSERT INTO purchase_order_item (order_id, line_no, sku_id, product_name, sku_label, quantity,"
                            + " unit_price_cents, line_total_cents) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    id,
                    line++,
                    item.skuId(),
                    item.productName(),
                    item.skuLabel(),
                    item.quantity(),
                    item.unitPriceCents(),
                    item.lineTotalCents());
        }
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appendHistory(
            UUID id,
            int sequence,
            OrderStatus from,
            OrderStatus to,
            OrderActor actor,
            String reason,
            Instant now,
            UUID correlationId) {
        jdbc.update(
                "INSERT INTO purchase_order_status_history (order_id, sequence, from_status, to_status, actor,"
                        + " reason, occurred_at, correlation_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                sequence,
                from == null ? null : from.name(),
                to.name(),
                actor.name(),
                reason,
                Timestamp.from(now),
                correlationId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateStatus(UUID id, OrderStatus status, int sequence, Instant now) {
        jdbc.update(
                "UPDATE purchase_order SET status = ?, status_sequence = ?, updated_at = ? WHERE id = ?",
                status.name(),
                sequence,
                Timestamp.from(now),
                id);
    }

    private static OrderHead head(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new OrderHead(
                rs.getObject("id", UUID.class),
                rs.getString("checkout_key"),
                FulfillmentMode.valueOf(rs.getString("fulfillment_mode")),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getInt("status_sequence"),
                rs.getLong("total_cents"));
    }

    public record OrderHead(
            UUID id, String checkoutKey, FulfillmentMode mode, OrderStatus status, int sequence, long totalCents) {}

    public record HistoryRow(
            int sequence, OrderStatus from, OrderStatus to, OrderActor actor, String reason, Instant occurredAt) {}
}
