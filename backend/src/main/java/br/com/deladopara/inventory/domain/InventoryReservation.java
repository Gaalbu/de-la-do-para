package br.com.deladopara.inventory.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record InventoryReservation(UUID id, String reference, Instant createdAt, Instant expiresAt, Status status) {

    private static final Duration HOLD = Duration.ofMinutes(15);

    public InventoryReservation {
        if (id == null
                || reference == null
                || reference.isBlank()
                || createdAt == null
                || expiresAt == null
                || status == null
                || !expiresAt.equals(createdAt.plus(HOLD))) {
            throw new IllegalArgumentException("Invalid inventory reservation");
        }
    }

    public static InventoryReservation create(UUID id, String reference, Instant createdAt) {
        return new InventoryReservation(id, reference, createdAt, createdAt.plus(HOLD), Status.ACTIVE);
    }

    public boolean isExpiredAt(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Clock instant is required");
        }
        return !now.isBefore(expiresAt);
    }

    public enum Status {
        ACTIVE,
        COMMITTED,
        RELEASED
    }
}
