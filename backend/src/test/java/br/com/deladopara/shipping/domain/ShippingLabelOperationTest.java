package br.com.deladopara.shipping.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShippingLabelOperationTest {

    private static final UUID ORDER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID UNIT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CORRELATION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-10-03T12:00:00Z");

    @Test
    void recordsTheProviderIdOnlyAfterSuccessfulExternalCall() {
        var ready = operation();

        var requested = ready.request(CREATED_AT.plusSeconds(1));
        var completed = requested.succeed("carrier-shipment-42", CREATED_AT.plusSeconds(2));

        assertThat(requested.state()).isEqualTo(ShippingLabelOperation.State.REQUESTED);
        assertThat(requested.providerShipmentId()).isNull();
        assertThat(completed.state()).isEqualTo(ShippingLabelOperation.State.SUCCEEDED);
        assertThat(completed.providerShipmentId()).isEqualTo("carrier-shipment-42");
        assertThat(completed.packageSequences()).containsExactly(1, 2);
    }

    @Test
    void requiresTheKnownProviderIdWhenPreparingPurchaseAndGenerationSteps() {
        for (var step : List.of(ShippingLabelOperation.Step.PURCHASE, ShippingLabelOperation.Step.GENERATE)) {
            assertThatThrownBy(() -> ShippingLabelOperation.ready(
                            UUID.randomUUID(), ORDER_ID, UNIT_ID, List.of(1, 2), step, CORRELATION_ID, CREATED_AT))
                    .isInstanceOf(IllegalArgumentException.class);

            var ready = ShippingLabelOperation.ready(
                    UUID.randomUUID(),
                    ORDER_ID,
                    UNIT_ID,
                    List.of(1, 2),
                    step,
                    CORRELATION_ID,
                    "carrier-shipment-42",
                    CREATED_AT);
            var completed = ready.request(CREATED_AT.plusSeconds(1)).succeed(null, CREATED_AT.plusSeconds(2));

            assertThat(completed.step()).isEqualTo(step);
            assertThat(completed.providerShipmentId()).isEqualTo("carrier-shipment-42");
        }
    }

    @Test
    void keepsAnAmbiguousWriteUnknownAndRejectsAnAutomaticResend() {
        var unknown = operation().request(CREATED_AT.plusSeconds(1)).markUnknown(CREATED_AT.plusSeconds(30));

        assertThat(unknown.state()).isEqualTo(ShippingLabelOperation.State.UNKNOWN);
        assertThat(unknown.providerShipmentId()).isNull();
        assertThatThrownBy(() -> unknown.request(CREATED_AT.plusSeconds(31))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsAnExplicitReconciliationToResolveAnUnknownWrite() {
        var unknown = operation().request(CREATED_AT.plusSeconds(1)).markUnknown(CREATED_AT.plusSeconds(30));

        var reconciled = unknown.reconcileSucceeded("carrier-shipment-42", CREATED_AT.plusSeconds(60));

        assertThat(reconciled.state()).isEqualTo(ShippingLabelOperation.State.SUCCEEDED);
        assertThat(reconciled.providerShipmentId()).isEqualTo("carrier-shipment-42");
        assertThatThrownBy(() -> operation().reconcileSucceeded("carrier-shipment-42", CREATED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordsOnlySanitizedDeterministicFailureCodes() {
        var failed =
                operation().request(CREATED_AT.plusSeconds(1)).fail("INVALID_RECIPIENT", CREATED_AT.plusSeconds(2));

        assertThat(failed.state()).isEqualTo(ShippingLabelOperation.State.FAILED);
        assertThat(failed.failureCode()).isEqualTo("INVALID_RECIPIENT");
        assertThatThrownBy(() -> operation()
                        .request(CREATED_AT.plusSeconds(1))
                        .fail("provider said: invalid document 123", CREATED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidOrDuplicatePackageSequences() {
        assertThatThrownBy(() -> ShippingLabelOperation.ready(
                        UUID.randomUUID(),
                        ORDER_ID,
                        UNIT_ID,
                        List.of(1, 1),
                        ShippingLabelOperation.Step.ADD_TO_CART,
                        CORRELATION_ID,
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShippingLabelOperation.ready(
                        UUID.randomUUID(),
                        ORDER_ID,
                        UNIT_ID,
                        List.of(0),
                        ShippingLabelOperation.Step.ADD_TO_CART,
                        CORRELATION_ID,
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ShippingLabelOperation operation() {
        return ShippingLabelOperation.ready(
                UUID.randomUUID(),
                ORDER_ID,
                UNIT_ID,
                List.of(1, 2),
                ShippingLabelOperation.Step.ADD_TO_CART,
                CORRELATION_ID,
                CREATED_AT);
    }
}
