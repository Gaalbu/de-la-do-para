package br.com.deladopara.payments.adapter.persistence;

import br.com.deladopara.payments.domain.OperationKind;
import br.com.deladopara.payments.domain.OperationStatus;
import br.com.deladopara.payments.domain.PaymentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
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
public class PaymentRepository {

    private final JdbcTemplate jdbc;

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Intent> findByOrder(UUID orderId) {
        return jdbc
                .query("SELECT * FROM payment_intent WHERE order_id = ?", PaymentRepository::intent, orderId)
                .stream()
                .findFirst();
    }

    public Optional<Intent> find(UUID id) {
        return jdbc.query("SELECT * FROM payment_intent WHERE id = ?", PaymentRepository::intent, id).stream()
                .findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Intent> lock(UUID id) {
        return jdbc
                .query("SELECT * FROM payment_intent WHERE id = ? FOR UPDATE", PaymentRepository::intent, id)
                .stream()
                .findFirst();
    }

    /** Returns false when the order already has an intent. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean insertIntent(UUID id, UUID orderId, long amountCents, Instant now) {
        return jdbc.update("""
                        INSERT INTO payment_intent (id, order_id, amount_cents, currency, status, status_version,
                                                    created_at, updated_at)
                        VALUES (?, ?, ?, 'BRL', 'REQUESTED', 0, ?, ?)
                        ON CONFLICT (order_id) DO NOTHING
                        """, id, orderId, amountCents, Timestamp.from(now), Timestamp.from(now)) > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateStatus(UUID id, PaymentStatus status, int version, String reason, Instant now) {
        jdbc.update(
                "UPDATE payment_intent SET status = ?, status_version = ?, status_reason = ?, updated_at = ?"
                        + " WHERE id = ?",
                status.name(),
                version,
                reason,
                Timestamp.from(now),
                id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertOperation(UUID id, UUID intentId, OperationKind kind, Instant now) {
        jdbc.update(
                "INSERT INTO payment_external_operation (id, intent_id, kind, status, created_at)"
                        + " VALUES (?, ?, ?, 'PENDING', ?)",
                id,
                intentId,
                kind.name(),
                Timestamp.from(now));
    }

    public Optional<Operation> findOperation(UUID id) {
        return jdbc
                .query("SELECT * FROM payment_external_operation WHERE id = ?", PaymentRepository::operation, id)
                .stream()
                .findFirst();
    }

    /** Claims the oldest pending CREATE_CHECKOUT operation without waiting on rows another worker holds. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Operation> claimPendingCheckout(Instant now, Instant leaseUntil) {
        return jdbc.query("""
                        UPDATE payment_external_operation SET status = 'IN_FLIGHT', lease_until = ?, started_at = ?
                        WHERE id = (SELECT id FROM payment_external_operation
                                    WHERE status = 'PENDING' AND kind = 'CREATE_CHECKOUT'
                                    ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED)
                        RETURNING *
                        """, PaymentRepository::operation, Timestamp.from(leaseUntil), Timestamp.from(now)).stream()
                .findFirst();
    }

    /** Only an IN_FLIGHT operation can finish; returns false for a late or repeated result. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean finishOperation(UUID id, OperationStatus status, String diagnostic, Instant now) {
        return jdbc.update(
                        "UPDATE payment_external_operation SET status = ?, lease_until = NULL, finished_at = ?,"
                                + " last_error = ? WHERE id = ? AND status = 'IN_FLIGHT'",
                        status.name(),
                        Timestamp.from(now),
                        diagnostic,
                        id)
                > 0;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Operation> lockAbandonedInFlight(Instant now) {
        return jdbc.query(
                "SELECT * FROM payment_external_operation WHERE status = 'IN_FLIGHT' AND lease_until < ?"
                        + " ORDER BY lease_until FOR UPDATE SKIP LOCKED",
                PaymentRepository::operation,
                Timestamp.from(now));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void storeCheckout(UUID intentId, String checkoutId, String url, Instant expiresAt) {
        jdbc.update(
                "UPDATE payment_intent SET provider_checkout_id = ?, checkout_url = ?, checkout_expires_at = ?"
                        + " WHERE id = ?",
                checkoutId,
                url,
                expiresAt == null ? null : Timestamp.from(expiresAt),
                intentId);
    }

    public Instant checkoutExpiresAt(UUID intentId) {
        var value = jdbc.queryForObject(
                "SELECT checkout_expires_at FROM payment_intent WHERE id = ?", Timestamp.class, intentId);
        return value == null ? null : value.toInstant();
    }

    private static Intent intent(ResultSet rs, int row) throws SQLException {
        return new Intent(
                rs.getObject("id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getLong("amount_cents"),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getInt("status_version"));
    }

    static Operation operation(ResultSet rs, int row) throws SQLException {
        var lease = rs.getTimestamp("lease_until");
        return new Operation(
                rs.getObject("id", UUID.class),
                rs.getObject("intent_id", UUID.class),
                OperationKind.valueOf(rs.getString("kind")),
                OperationStatus.valueOf(rs.getString("status")),
                lease == null ? null : lease.toInstant(),
                rs.getString("last_error"));
    }

    public record Intent(UUID id, UUID orderId, long amountCents, PaymentStatus status, int version) {}

    public record Operation(
            UUID id, UUID intentId, OperationKind kind, OperationStatus status, Instant leaseUntil, String lastError) {}
}
