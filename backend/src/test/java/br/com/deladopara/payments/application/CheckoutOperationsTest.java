package br.com.deladopara.payments.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CheckoutOperationsTest {

    @Test
    void leaseMustBePositive() {
        for (var lease : new Duration[] {Duration.ZERO, Duration.ofSeconds(-1)}) {
            assertThatThrownBy(() -> new CheckoutOperations(null, null, Clock.systemUTC(), lease))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("payments.worker.lease");
        }
    }
}
