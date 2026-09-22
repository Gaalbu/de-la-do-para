package br.com.deladopara.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryLedgerTest {

    @Test
    void replaysTheSameMovementWithoutDuplicatingIt() {
        var ledger = new InventoryLedger();
        var movement = movement("receipt-1", 10);

        assertThat(ledger.append(movement)).isSameAs(movement);
        assertThat(ledger.append(movement)).isSameAs(movement);
        assertThat(ledger.size()).isOne();
    }

    @Test
    void rejectsReusingAnIdempotencyKeyWithDifferentPayload() {
        var ledger = new InventoryLedger();
        ledger.append(movement("receipt-1", 10));

        assertThatThrownBy(() -> ledger.append(movement("receipt-1", 11))).isInstanceOf(IllegalArgumentException.class);
    }

    private static InventoryMovement movement(String key, int delta) {
        return new InventoryMovement(
                UUID.randomUUID(),
                "FARINHA",
                InventoryMovement.Type.RECEIPT,
                delta,
                key,
                null,
                "entrada",
                Instant.parse("2026-10-01T12:00:00Z"));
    }
}
