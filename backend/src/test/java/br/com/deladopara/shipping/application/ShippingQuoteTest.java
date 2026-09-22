package br.com.deladopara.shipping.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShippingQuoteTest {

    @Test
    void rejectsQuoteThatExpiresBeforeItWasCreated() {
        var now = Instant.parse("2026-09-22T12:00:00Z");

        assertThatThrownBy(() -> new ShippingQuote(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        2,
                        "66053000",
                        "fingerprint",
                        "sandbox-pac",
                        "Sandbox PAC",
                        2590,
                        5,
                        2,
                        List.of(1),
                        now,
                        now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("shipping quote validity is invalid");
    }
}
