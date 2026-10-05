package br.com.deladopara.notifications.adapter.persistence;

import br.com.deladopara.eventing.application.EventEnvelope;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OrderNotificationRepository {

    private final JdbcTemplate jdbc;

    public OrderNotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(EventEnvelope event, Instant now) {
        var orderId = UUID.fromString(event.aggregateId());
        jdbc.update(
                """
                INSERT INTO order_notification_outbox
                    (id, event_id, template_version, recipient, order_id, event_type, aggregate_version, order_status,
                     fulfillment_mode, total_cents, status, available_at, created_at)
                SELECT ?, ?, 1, o.contact_email, o.id, ?, ?, ?, o.fulfillment_mode, o.total_cents, 'PENDING', ?, ?
                FROM purchase_order o WHERE o.id = ?
                ON CONFLICT (event_id, template_version, recipient) DO NOTHING
                """,
                UUID.randomUUID(),
                event.eventId(),
                event.eventType(),
                event.aggregateVersion(),
                "order.created".equals(event.eventType())
                        ? "PENDING_PAYMENT"
                        : event.payload().path("to").asText(),
                Timestamp.from(now),
                Timestamp.from(now),
                orderId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<QueuedMail> claim(Instant now, Instant leaseUntil) {
        var rows = jdbc.query(
                """
                SELECT notification.*,
                    CASE WHEN notification.order_status = 'READY_FOR_PICKUP'
                              AND notification.fulfillment_mode = 'PICKUP'
                         THEN order_record.destination ->> 'label' END AS pickup_point,
                    CASE WHEN notification.order_status = 'READY_FOR_PICKUP'
                              AND notification.fulfillment_mode = 'PICKUP'
                         THEN order_record.destination ->> 'window' END AS pickup_window
                FROM order_notification_outbox notification
                JOIN purchase_order order_record ON order_record.id = notification.order_id
                WHERE notification.status = 'PENDING'
                    AND notification.available_at <= ?
                    AND (notification.lease_until IS NULL OR notification.lease_until <= ?)
                    AND NOT EXISTS (
                        SELECT 1 FROM order_notification_outbox earlier
                        WHERE earlier.order_id = notification.order_id
                            AND earlier.aggregate_version < notification.aggregate_version
                            AND earlier.status IN ('PENDING', 'FAILED', 'UNKNOWN')
                    )
                ORDER BY notification.available_at, notification.created_at
                FOR UPDATE OF notification SKIP LOCKED LIMIT 1
                """,
                (rs, row) -> new QueuedMail(
                        rs.getObject("id", UUID.class),
                        rs.getString("recipient"),
                        rs.getObject("order_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getLong("aggregate_version"),
                        rs.getString("order_status"),
                        rs.getString("fulfillment_mode"),
                        rs.getLong("total_cents"),
                        rs.getString("pickup_point"),
                        rs.getString("pickup_window"),
                        rs.getInt("attempt_count") + 1),
                Timestamp.from(now),
                Timestamp.from(leaseUntil));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var mail = rows.getFirst();
        jdbc.update(
                "UPDATE order_notification_outbox SET lease_until = ?, attempt_count = ? WHERE id = ?",
                Timestamp.from(leaseUntil),
                mail.attemptCount(),
                mail.id());
        return Optional.of(mail);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void accepted(UUID id, Instant now) {
        jdbc.update("""
                UPDATE order_notification_outbox SET status='ACCEPTED', accepted_at=?, lease_until=NULL,
                    last_error_code=NULL WHERE id=? AND status='PENDING'
                """, Timestamp.from(now), id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void retry(UUID id, Instant availableAt, String safeErrorCode) {
        jdbc.update("""
                UPDATE order_notification_outbox SET available_at=?, lease_until=NULL, last_error_code=?
                WHERE id=? AND status='PENDING'
                """, Timestamp.from(availableAt), safeErrorCode, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void unknown(UUID id, String safeErrorCode) {
        jdbc.update("""
                UPDATE order_notification_outbox SET status='UNKNOWN', lease_until=NULL, last_error_code=?
                WHERE id=? AND status='PENDING'
                """, safeErrorCode, id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void failed(UUID id, String safeErrorCode) {
        jdbc.update("""
                UPDATE order_notification_outbox SET status='FAILED', lease_until=NULL, last_error_code=?
                WHERE id=? AND status='PENDING'
                """, safeErrorCode, id);
    }

    public record QueuedMail(
            UUID id,
            String recipient,
            UUID orderId,
            String eventType,
            long aggregateVersion,
            String orderStatus,
            String fulfillmentMode,
            long totalCents,
            String pickupPoint,
            String pickupWindow,
            int attemptCount) {}
}
