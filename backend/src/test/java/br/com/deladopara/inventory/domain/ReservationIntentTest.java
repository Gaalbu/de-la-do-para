package br.com.deladopara.inventory.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReservationIntentTest {

    @Test
    void rejectsDuplicateSkuAndNonPositiveQuantities() {
        assertThatThrownBy(() -> new ReservationIntent(
                        "checkout-1",
                        LocalDate.of(2026, 10, 1),
                        List.of(new ReservationIntent.Line("FARINHA", 1), new ReservationIntent.Line("FARINHA", 2))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationIntent(
                        "checkout-1", LocalDate.of(2026, 10, 1), List.of(new ReservationIntent.Line("FARINHA", 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIncompleteIntent() {
        assertThatThrownBy(() -> new ReservationIntent(
                        " ", LocalDate.of(2026, 10, 1), List.of(new ReservationIntent.Line("FARINHA", 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsTotalUnitsWithoutChangingTheIndividualLines() {
        var intent = new ReservationIntent(
                "checkout-1",
                LocalDate.of(2026, 10, 1),
                List.of(new ReservationIntent.Line("FARINHA", 2), new ReservationIntent.Line("CUIA", 3)));

        org.assertj.core.api.Assertions.assertThat(intent.totalUnits()).isEqualTo(5);
    }
}
