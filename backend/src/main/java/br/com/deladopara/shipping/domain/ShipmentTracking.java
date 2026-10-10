package br.com.deladopara.shipping.domain;

import java.time.Instant;

/** Monotonic tracking progress for the package set attached to one provider shipment id. */
public record ShipmentTracking(String eventType, Progress progress, boolean exception, Instant occurredAt) {

    public ShipmentTracking {
        if (eventType == null || progress == null || occurredAt == null) {
            throw new IllegalArgumentException("shipment tracking state is required");
        }
    }

    public ShipmentTracking apply(String nextEvent, Instant nextOccurredAt) {
        var next = from(nextEvent, nextOccurredAt);
        if (progress == Progress.CANCELLED && next.progress != Progress.CANCELLED) {
            if (nextOccurredAt.isAfter(occurredAt)) {
                return new ShipmentTracking(nextEvent, Progress.CANCELLED, true, nextOccurredAt);
            }
            return this;
        }
        if (next.exception && nextOccurredAt.isAfter(occurredAt)) {
            return new ShipmentTracking(nextEvent, progress, true, nextOccurredAt);
        }
        if (next.progress.rank < progress.rank) {
            return this;
        }
        if (next.progress == progress && next.exception && !exception) {
            return new ShipmentTracking(nextEvent, progress, true, max(occurredAt, nextOccurredAt));
        }
        if (next.progress == progress && !next.exception && exception && nextOccurredAt.isBefore(occurredAt)) {
            return this;
        }
        return next;
    }

    public static ShipmentTracking from(String event, Instant occurredAt) {
        return switch (event) {
            case "order.created", "order.pending", "order.released", "order.generated" ->
                new ShipmentTracking(event, Progress.LABEL_CREATED, false, occurredAt);
            case "order.received" -> new ShipmentTracking(event, Progress.HANDED_TO_CARRIER, false, occurredAt);
            case "order.posted" -> new ShipmentTracking(event, Progress.IN_TRANSIT, false, occurredAt);
            case "order.delivered" -> new ShipmentTracking(event, Progress.DELIVERED, false, occurredAt);
            case "order.cancelled" -> new ShipmentTracking(event, Progress.CANCELLED, true, occurredAt);
            case "order.undelivered", "order.paused", "order.suspended" ->
                new ShipmentTracking(event, Progress.UNDER_REVIEW, true, occurredAt);
            default -> throw new IllegalArgumentException("unsupported shipment tracking event");
        };
    }

    public static ShipmentTracking fromProviderStatus(String status, Instant observedAt) {
        var event =
                switch (status) {
                    case "created" -> "order.created";
                    case "pending" -> "order.pending";
                    case "released" -> "order.released";
                    case "generated" -> "order.generated";
                    case "received" -> "order.received";
                    case "posted" -> "order.posted";
                    case "delivered" -> "order.delivered";
                    case "cancelled", "canceled" -> "order.cancelled";
                    case "undelivered" -> "order.undelivered";
                    case "paused" -> "order.paused";
                    case "suspended" -> "order.suspended";
                    default -> throw new IllegalArgumentException("unsupported shipment tracking status");
                };
        return from(event, observedAt);
    }

    private static Instant max(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    public enum Progress {
        LABEL_CREATED(0),
        HANDED_TO_CARRIER(1),
        IN_TRANSIT(2),
        DELIVERED(3),
        UNDER_REVIEW(0),
        CANCELLED(0);

        private final int rank;

        Progress(int rank) {
            this.rank = rank;
        }
    }
}
