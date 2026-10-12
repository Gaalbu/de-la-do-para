package br.com.deladopara.shipping.application;

import br.com.deladopara.orders.domain.FulfillmentMode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reads sanitized per-shipment progress for the customer order view. */
@Service
public class ShippingProgressQueryService {

    private static final TypeReference<List<Integer>> PACKAGE_SEQUENCES = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ShippingProgressQueryService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public DeliveryProgress find(UUID orderId) {
        var shipments = jdbc.query("""
                SELECT operation.package_sequences::text AS package_sequences,
                       operation.step,
                       operation.state,
                       operation.updated_at,
                       tracking.progress AS tracking_progress,
                       tracking.exception AS tracking_exception,
                       tracking.occurred_at AS tracking_occurred_at,
                       tracking.tracking_count
                  FROM shipping_label_operation operation
                  LEFT JOIN LATERAL (
                      SELECT package_tracking.progress, package_tracking.exception, package_tracking.occurred_at,
                             count(*) OVER () AS tracking_count
                        FROM shipping_package_tracking package_tracking
                       WHERE package_tracking.order_id = operation.order_id
                         AND package_tracking.shipment_unit_id = operation.shipment_unit_id
                         AND package_tracking.package_sequences = operation.package_sequences
                       ORDER BY package_tracking.occurred_at DESC, package_tracking.provider_shipment_id
                       LIMIT 1
                  ) tracking ON TRUE
                 WHERE operation.order_id = ?
                   AND operation.step = (
                       SELECT candidate.step
                         FROM shipping_label_operation candidate
                        WHERE candidate.order_id = operation.order_id
                          AND candidate.shipment_unit_id = operation.shipment_unit_id
                        ORDER BY CASE candidate.step
                                     WHEN 'GENERATE' THEN 3
                                     WHEN 'PURCHASE' THEN 2
                                     ELSE 1
                                 END DESC
                        LIMIT 1
                   )
                 ORDER BY operation.created_at, operation.shipment_unit_id
                """, (rs, row) -> toShipment(rs.getString("package_sequences"), rs), orderId);
        return new DeliveryProgress(
                orderId,
                FulfillmentMode.DELIVERY,
                shipments.stream()
                        .sorted(Comparator.comparing(
                                shipment -> shipment.packageSequences().getFirst()))
                        .toList());
    }

    private ShipmentProgress toShipment(String packageSequencesJson, java.sql.ResultSet rs)
            throws java.sql.SQLException {
        try {
            var packageSequences = objectMapper.readValue(packageSequencesJson, PACKAGE_SEQUENCES);
            var trackedProgress = rs.getString("tracking_progress");
            if (trackedProgress != null) {
                if (rs.getLong("tracking_count") > 1) {
                    return new ShipmentProgress(
                            packageSequences, "UNDER_REVIEW", true, instant(rs.getTimestamp("tracking_occurred_at")));
                }
                return new ShipmentProgress(
                        packageSequences,
                        trackedProgress,
                        rs.getBoolean("tracking_exception"),
                        instant(rs.getTimestamp("tracking_occurred_at")));
            }

            var state = rs.getString("state");
            var step = rs.getString("step");
            if ("UNKNOWN".equals(state) || "FAILED".equals(state)) {
                return new ShipmentProgress(
                        packageSequences, "UNDER_REVIEW", true, instant(rs.getTimestamp("updated_at")));
            }
            var status = "GENERATE".equals(step) && "SUCCEEDED".equals(state) ? "LABEL_CREATED" : "PREPARING_LABEL";
            return new ShipmentProgress(packageSequences, status, false, instant(rs.getTimestamp("updated_at")));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("stored shipping package mapping is invalid", exception);
        }
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record DeliveryProgress(UUID orderId, FulfillmentMode fulfillmentMode, List<ShipmentProgress> shipments) {
        public DeliveryProgress {
            shipments = List.copyOf(shipments);
        }
    }

    public record ShipmentProgress(
            List<Integer> packageSequences, String status, boolean exception, Instant updatedAt) {
        public ShipmentProgress {
            packageSequences = List.copyOf(packageSequences);
        }
    }
}
