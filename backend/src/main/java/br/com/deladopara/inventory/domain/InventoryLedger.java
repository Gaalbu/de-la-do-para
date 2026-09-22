package br.com.deladopara.inventory.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public final class InventoryLedger {

    private final Map<String, InventoryMovement> movements = new LinkedHashMap<>();

    public InventoryMovement append(InventoryMovement movement) {
        var previous = movements.putIfAbsent(movement.idempotencyKey(), movement);
        if (previous == null || previous.equals(movement)) {
            return previous == null ? movement : previous;
        }
        throw new IllegalArgumentException("Idempotency key already has another movement");
    }

    public int size() {
        return movements.size();
    }
}
