package br.com.deladopara.shipping.application;

import br.com.deladopara.shipping.domain.ShipmentTracking;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores only allowlisted tracking facts; the provider body is never retained. */
@Service
public class ShipmentTrackingService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ShipmentTrackingService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public boolean record(String providerShipmentId, String eventType, Instant occurredAt, byte[] body) {
        var event = ShipmentTracking.from(eventType, occurredAt);
        var hash = HexFormat.of().formatHex(sha256(body));
        if (jdbc.update(
                        """
                        INSERT INTO shipping_tracking_event
                            (payload_hash, provider_shipment_id, event_type, progress, exception, occurred_at, received_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (payload_hash) DO NOTHING
                        """,
                        hash,
                        providerShipmentId,
                        event.eventType(),
                        event.progress().name(),
                        event.exception(),
                        Timestamp.from(occurredAt),
                        Timestamp.from(clock.instant()))
                == 0) {
            return false;
        }

        var rows = jdbc.query(
                """
                SELECT order_id, shipment_unit_id, package_sequences, event_type, progress, exception, occurred_at
                  FROM shipping_package_tracking WHERE provider_shipment_id = ? FOR UPDATE
                """,
                (rs, row) -> new Existing(
                        rs.getObject("order_id", java.util.UUID.class),
                        rs.getObject("shipment_unit_id", java.util.UUID.class),
                        rs.getString("package_sequences"),
                        rs.getString("event_type"),
                        rs.getString("progress"),
                        rs.getBoolean("exception"),
                        rs.getTimestamp("occurred_at").toInstant()),
                providerShipmentId);
        if (rows.isEmpty()) {
            createTracking(providerShipmentId, event, occurredAt);
            return true;
        }

        var prior = rows.getFirst();
        updateTracking(providerShipmentId, eventType, occurredAt, prior);
        return true;
    }

    private void createTracking(String providerShipmentId, ShipmentTracking event, Instant occurredAt) {
        var mapping = jdbc.query(
                """
                SELECT order_id, shipment_unit_id, package_sequences
                  FROM shipping_label_operation
                 WHERE provider_shipment_id = ? AND step = 'GENERATE' AND state = 'SUCCEEDED'
                """,
                (rs, row) -> new Mapping(
                        rs.getObject("order_id", java.util.UUID.class),
                        rs.getObject("shipment_unit_id", java.util.UUID.class),
                        rs.getString("package_sequences")),
                providerShipmentId);
        if (mapping.isEmpty()) {
            throw new IllegalArgumentException("shipment tracking id is not associated with a generated label");
        }
        var value = mapping.getFirst();
        jdbc.update(
                """
                INSERT INTO shipping_package_tracking
                    (provider_shipment_id, order_id, shipment_unit_id, package_sequences, event_type,
                     progress, exception, occurred_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                """,
                providerShipmentId,
                value.orderId,
                value.unitId,
                value.packages,
                event.eventType(),
                event.progress().name(),
                event.exception(),
                Timestamp.from(occurredAt));
    }

    private void updateTracking(String providerShipmentId, String eventType, Instant occurredAt, Existing prior) {
        var state = new ShipmentTracking(
                        prior.eventType,
                        ShipmentTracking.Progress.valueOf(prior.progress),
                        prior.exception,
                        prior.occurredAt)
                .apply(eventType, occurredAt);
        if (state.occurredAt().isAfter(prior.occurredAt)
                || state.exception() != prior.exception
                || !state.eventType().equals(prior.eventType)) {
            jdbc.update(
                    """
                    UPDATE shipping_package_tracking
                       SET event_type = ?, progress = ?, exception = ?, occurred_at = ?
                     WHERE provider_shipment_id = ?
                    """,
                    state.eventType(),
                    state.progress().name(),
                    state.exception(),
                    Timestamp.from(state.occurredAt()),
                    providerShipmentId);
        }
    }

    private byte[] sha256(byte[] body) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(body);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Mapping(java.util.UUID orderId, java.util.UUID unitId, String packages) {}

    private record Existing(
            java.util.UUID orderId,
            java.util.UUID unitId,
            String packages,
            String eventType,
            String progress,
            boolean exception,
            Instant occurredAt) {}
}
