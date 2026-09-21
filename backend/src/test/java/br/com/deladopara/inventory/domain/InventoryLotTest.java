package br.com.deladopara.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InventoryLotTest {

    private static final LocalDate ARRIVAL = LocalDate.of(2026, 10, 1);

    @Test
    void calculatesFreeAndAvailabilityAtExactShelfLifeMargin() {
        var lot = new InventoryLot("FARINHA", 10, 3, false, LocalDate.of(2026, 10, 31), 30);

        assertThat(lot.freeUnits()).isEqualTo(7);
        assertThat(lot.availableFor(ARRIVAL)).isEqualTo(7);
    }

    @Test
    void rejectsOneDayBelowMarginAndBlockedLots() {
        var shortLot = new InventoryLot("FARINHA", 10, 3, false, LocalDate.of(2026, 10, 30), 30);
        var blockedLot = new InventoryLot("FARINHA", 10, 3, true, LocalDate.of(2026, 11, 30), 30);

        assertThat(shortLot.availableFor(ARRIVAL)).isZero();
        assertThat(blockedLot.availableFor(ARRIVAL)).isZero();
    }

    @Test
    void craftsDoNotRequireExpiry() {
        var lot = new InventoryLot("CUIA", 4, 1, false, null, null);

        assertThat(lot.availableFor(ARRIVAL)).isEqualTo(3);
    }

    @Test
    void rejectsInvalidBalancesAndDates() {
        assertThatThrownBy(() -> new InventoryLot("FARINHA", 2, 3, false, LocalDate.of(2026, 12, 1), 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InventoryLot("FARINHA", 1, 0, false, null, 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InventoryLot("FARINHA", 1, 0, false, LocalDate.of(2026, 12, 1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InventoryLot("FARINHA", 1, 0, false, null, 30).availableFor(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
