package br.com.deladopara.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryReservationTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void expiresAtExactlyFifteenMinutesAndAtTheBoundary() {
        var reservation = InventoryReservation.create(UUID.randomUUID(), "checkout-1", CREATED);

        assertThat(reservation.expiresAt()).isEqualTo(Instant.parse("2026-10-01T12:15:00Z"));
        assertThat(reservation.isExpiredAt(CREATED.plusSeconds(899))).isFalse();
        assertThat(reservation.isExpiredAt(reservation.expiresAt())).isTrue();
    }

    @Test
    void rejectsMalformedReservationsAndMissingClock() {
        assertThatThrownBy(() -> InventoryReservation.create(UUID.randomUUID(), " ", CREATED))
                .isInstanceOf(IllegalArgumentException.class);
        var reservation = InventoryReservation.create(UUID.randomUUID(), "checkout-1", CREATED);
        assertThatThrownBy(() -> reservation.isExpiredAt(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
