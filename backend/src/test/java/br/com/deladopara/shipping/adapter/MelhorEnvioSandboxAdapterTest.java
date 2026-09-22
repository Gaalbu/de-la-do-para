package br.com.deladopara.shipping.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.shipping.application.ShippingAdapterException;
import br.com.deladopara.shipping.application.ShippingQuoteRequest;
import br.com.deladopara.shipping.domain.PackageComposer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MelhorEnvioSandboxAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    private final MelhorEnvioSandboxAdapter adapter = new MelhorEnvioSandboxAdapter(new ObjectMapper());

    @Test
    void parsesQuoteAndRequiresEveryComposedPackage() {
        var packages = PackageComposer.compose(List.of(new PackageComposer.Line(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                PackageComposer.Category.FOOD,
                false,
                1,
                100,
                100,
                100,
                200)));

        var result = adapter.quote(new ShippingQuoteRequest("66053-000", packages), """
                {"quote":{"service_id":"sandbox-pac","service_name":"Sandbox PAC","price_cents":2590,"delivery_days":5,"expires_at":"2026-09-22T13:00:00Z","package_sequences":[1]}}
                """, NOW);

        assertThat(result.priceCents()).isEqualTo(2590);
        assertThat(result.packageSequences()).containsExactly(1);
    }

    @Test
    void rejectsExpiredQuoteAndIncompleteCoverage() {
        var packages = PackageComposer.compose(List.of(new PackageComposer.Line(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                PackageComposer.Category.CRAFT,
                false,
                1,
                100,
                100,
                100,
                200)));
        var request = new ShippingQuoteRequest("66053000", packages);

        assertThatThrownBy(() -> adapter.quote(
                        request,
                        "{"
                                + "\"quote\":{\"service_id\":\"x\",\"service_name\":\"x\",\"price_cents\":1,\"delivery_days\":1,\"expires_at\":\"2026-09-22T11:00:00Z\",\"package_sequences\":[]}}",
                        NOW))
                .isInstanceOf(ShippingAdapterException.class);
    }

    @Test
    void validatesAdapterConfigurationWithoutExposingCredential() {
        assertThatThrownBy(() -> new ShippingAdapterProperties("http://sandbox", "", Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("shipping adapter configuration is incomplete");
    }
}
