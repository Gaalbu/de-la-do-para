package br.com.deladopara.orders.adapter.persistence;

import br.com.deladopara.orders.application.CreateOrderCommand;
import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
