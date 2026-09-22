package br.com.deladopara.inventory.domain;

import java.time.Instant;
import java.util.UUID;

public record InventoryMovement(
        UUID id,
        String skuCode,
        Type type,
        int unitsDelta,
        String idempotencyKey,
        UUID actorId,
        String reason,
        Instant occurredAt) {

    public InventoryMovement {
        if (id == null
                || skuCode == null
                || skuCode.isBlank()
                || type == null
                || unitsDelta == 0
                || idempotencyKey == null
                || idempotencyKey.isBlank()
                || reason == null
                || reason.isBlank()
                || occurredAt == null) {
            throw new IllegalArgumentException("Invalid inventory movement");
        }
        if (type.requiresActor() && actorId == null) {
            throw new IllegalArgumentException("Administrative movement requires actor");
        }
    }

    public enum Type {
        RECEIPT(false),
        RESERVATION(false),
        RELEASE(false),
        HANDOFF(false),
        ADMIN_ADJUSTMENT(true);

        private final boolean requiresActor;

        Type(boolean requiresActor) {
            this.requiresActor = requiresActor;
        }

        boolean requiresActor() {
            return requiresActor;
        }
    }
}
