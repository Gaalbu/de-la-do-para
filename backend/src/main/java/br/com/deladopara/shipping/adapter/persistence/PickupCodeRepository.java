package br.com.deladopara.shipping.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PickupCodeRepository {

    private final JdbcTemplate jdbc;

    public PickupCodeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(UUID orderId, String ciphertext, Instant issuedAt) {
        jdbc.update(
                "INSERT INTO shipping_pickup_code (order_id, pickup_status, ciphertext, issued_at)"
                        + " VALUES (?, 'READY_FOR_PICKUP', ?, ?)",
                orderId,
                ciphertext,
                Timestamp.from(issuedAt));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PickupCode> lock(UUID orderId) {
        return jdbc
                .query(
                        "SELECT order_id, pickup_status, ciphertext, issued_at, used_at FROM shipping_pickup_code"
                                + " WHERE order_id = ? FOR UPDATE",
                        (rs, row) -> new PickupCode(
                                rs.getObject("order_id", UUID.class),
                                rs.getString("pickup_status"),
                                rs.getString("ciphertext"),
                                rs.getTimestamp("issued_at").toInstant(),
                                rs.getTimestamp("used_at") == null
                                        ? null
                                        : rs.getTimestamp("used_at").toInstant()),
                        orderId)
                .stream()
                .findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(UUID orderId, Instant usedAt) {
        jdbc.update(
                "UPDATE shipping_pickup_code SET pickup_status = 'PICKED_UP', ciphertext = NULL, used_at = ?"
                        + " WHERE order_id = ? AND pickup_status = 'READY_FOR_PICKUP'",
                Timestamp.from(usedAt),
                orderId);
    }

    public record PickupCode(UUID orderId, String status, String ciphertext, Instant issuedAt, Instant usedAt) {}
}
