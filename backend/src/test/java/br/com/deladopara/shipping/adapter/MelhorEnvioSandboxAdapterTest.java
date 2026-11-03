package br.com.deladopara.shipping.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.deladopara.shipping.application.ShippingQuoteRequest;
import br.com.deladopara.shipping.domain.PackageComposer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MelhorEnvioSandboxAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final MelhorEnvioSandboxAdapter adapter = new MelhorEnvioSandboxAdapter(
            builder,
            new ObjectMapper(),
            new ShippingAdapterProperties(
                    "https://sandbox.melhorenvio.com.br",
                    "token",
                    Duration.ofSeconds(3),
                    Duration.ofMinutes(5),
                    "66053000",
                    "DeLaDoPara (contato configurado)"));

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

        server.expect(requestTo("https://sandbox.melhorenvio.com.br/api/v2/me/shipment/calculate"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(header("Accept", "application/json"))
                .andExpect(header("User-Agent", "DeLaDoPara (contato configurado)"))
                .andExpect(jsonPath("$.from.postal_code").value("66053000"))
                .andExpect(jsonPath("$.to.postal_code").value("66053000"))
                .andExpect(jsonPath("$.volumes[0].length").value(25.0))
                .andExpect(jsonPath("$.volumes[0].weight").value(0.4))
                .andExpect(jsonPath("$.volumes[0].insurance").value(12.34))
                .andRespond(withSuccess("""
                [{"id":1,"name":"PAC","custom_price":"25.90","custom_delivery_time":5,"packages":[{"price":"25.90","discount":"0.00","format":"box","dimensions":{"height":13,"width":19,"length":25},"weight":"0.4","insurance_value":"12.34","products":[]}]}]
                """, MediaType.APPLICATION_JSON));
        var result = adapter.quote(new ShippingQuoteRequest("66053000", "66053-000", packages, Map.of(1, 1234L)), NOW);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().priceCents()).isEqualTo(2590);
        assertThat(result.getFirst().packageSequences()).containsExactly(1);
        assertThat(result.getFirst().serviceId()).isEqualTo("1");
        server.verify();
    }

    @Test
    void returnsNoOptionsWhenCarrierHasNoAvailableService() {
        var packages = PackageComposer.compose(List.of(new PackageComposer.Line(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                PackageComposer.Category.CRAFT,
                false,
                1,
                100,
                100,
                100,
                200)));
        var request = new ShippingQuoteRequest("66053000", "66053000", packages, Map.of(1, 0L));

        server.expect(requestTo("https://sandbox.melhorenvio.com.br/api/v2/me/shipment/calculate"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(adapter.quote(request, NOW)).isEmpty();
        server.verify();
    }

    @Test
    void validatesAdapterConfigurationWithoutExposingCredential() {
        assertThatThrownBy(() -> new ShippingAdapterProperties(
                        "https://sandbox.melhorenvio.com.br",
                        "",
                        Duration.ofSeconds(2),
                        Duration.ofMinutes(5),
                        "66053000",
                        "agent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("shipping adapter configuration is incomplete");
        assertThatThrownBy(() -> new ShippingAdapterProperties(
                        "https://melhorenvio.com.br",
                        "token",
                        Duration.ofSeconds(2),
                        Duration.ofMinutes(5),
                        "66053000",
                        "agent"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
