package br.com.deladopara.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryAvailabilityTest {

    @Test
    void sumsOnlyEligibleLotsForTheRequestedSku() {
        var lots = List.of(
                new InventoryLot("FARINHA", 10, 2, false, LocalDate.of(2026, 10, 31), 30),
                new InventoryLot("FARINHA", 5, 5, false, LocalDate.of(2026, 10, 30), 30),
                new InventoryLot("CUIA", 20, 0, false, null, null));

        assertThat(InventoryAvailability.availableFor(lots, "FARINHA", LocalDate.of(2026, 10, 1)))
                .isEqualTo(8);
    }

    @Test
    void rejectsIncompleteQueriesAndNullLots() {
        assertThatThrownBy(() -> InventoryAvailability.availableFor(null, "FARINHA", LocalDate.of(2026, 10, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InventoryAvailability.availableFor(
                        Arrays.asList((InventoryLot) null), "FARINHA", LocalDate.of(2026, 10, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
