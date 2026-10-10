package br.com.deladopara.shipping.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ShipmentTrackingTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    void mapsDocumentedProviderEventsToLifecycleProgress() {
        assertThat(ShipmentTracking.from("order.posted", NOW).progress())
                .isEqualTo(ShipmentTracking.Progress.IN_TRANSIT);
        assertThat(ShipmentTracking.from("order.delivered", NOW).progress())
                .isEqualTo(ShipmentTracking.Progress.DELIVERED);
    }

    @Test
    void ignoresOutOfOrderProgressRegression() {
        var delivered = ShipmentTracking.from("order.delivered", NOW);

        assertThat(delivered.apply("order.posted", NOW.plusSeconds(1))).isSameAs(delivered);
    }

    @Test
    void keepsDeliveredProgressWhenCarrierReportsAnException() {
        var delivered = ShipmentTracking.from("order.delivered", NOW);
        var result = delivered.apply("order.undelivered", NOW.plusSeconds(1));

        assertThat(result.progress()).isEqualTo(ShipmentTracking.Progress.DELIVERED);
        assertThat(result.exception()).isTrue();
    }

    @Test
    void doesNotAdvanceACancelledShipmentWhenALaterStatusArrives() {
        var cancelled = ShipmentTracking.from("order.cancelled", NOW);

        var result = cancelled.apply("order.posted", NOW.plusSeconds(1));

        assertThat(result.progress()).isEqualTo(ShipmentTracking.Progress.CANCELLED);
        assertThat(result.exception()).isTrue();
    }

    @Test
    void rejectsUnknownProviderEvent() {
        assertThatThrownBy(() -> ShipmentTracking.from("order.unknown", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsProviderQueryStatusesToTheSameMonotonicLifecycle() {
        assertThat(ShipmentTracking.fromProviderStatus("posted", NOW).progress())
                .isEqualTo(ShipmentTracking.Progress.IN_TRANSIT);
        assertThat(ShipmentTracking.fromProviderStatus("delivered", NOW).progress())
                .isEqualTo(ShipmentTracking.Progress.DELIVERED);
        assertThat(ShipmentTracking.fromProviderStatus("undelivered", NOW).exception())
                .isTrue();
    }
}
