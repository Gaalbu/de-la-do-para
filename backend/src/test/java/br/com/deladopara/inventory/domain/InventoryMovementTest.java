package br.com.deladopara.inventory.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryMovementTest {

    @Test
    void requiresStableReferenceAndReason() {
        assertThatThrownBy(() -> new InventoryMovement(
                        UUID.randomUUID(),
                        "FARINHA",
                        InventoryMovement.Type.RECEIPT,
                        10,
                        " ",
                        null,
                        "entrada",
                        Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void administrativeAdjustmentRequiresActor() {
        assertThatThrownBy(() -> new InventoryMovement(
                        UUID.randomUUID(),
                        "FARINHA",
                        InventoryMovement.Type.ADMIN_ADJUSTMENT,
                        -1,
                        "adjustment-1",
                        null,
                        "contagem",
                        Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
