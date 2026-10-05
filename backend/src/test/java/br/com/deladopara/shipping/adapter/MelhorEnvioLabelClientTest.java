package br.com.deladopara.shipping.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.deladopara.shipping.application.ShippingProviderOperationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MelhorEnvioLabelClientTest {

    private static final String CART_URL = "https://sandbox.melhorenvio.com.br/api/v2/me/cart";
    private static final String CHECKOUT_URL = "https://sandbox.melhorenvio.com.br/api/v2/me/shipment/checkout";
    private static final String GENERATE_URL = "https://sandbox.melhorenvio.com.br/api/v2/me/shipment/generate";
    private static final String USER_AGENT = "De La do Pará (suporte@example.com)";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final MelhorEnvioLabelClient client =
            new MelhorEnvioLabelClient(builder, new ObjectMapper(), "sandbox-token", USER_AGENT);

    @Test
    void postsCartWithSandboxAuthorizationAndExactAcceptedSnapshotPayload() throws Exception {
        var payload = new ObjectMapper().readTree("""
                {"service":1,"from":{"postal_code":"66053000"},"to":{"postal_code":"01001000"},
                 "products":[{"name":"Produto teste","quantity":"1","unitary_value":"12.50"}],
                 "volumes":[{"height":10,"width":20,"length":30,"weight":0.5}]}
                """);
        server.expect(requestTo(CART_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sandbox-token"))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(payload.toString()))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"label-1\"}"));

        var response = client.addToCart(payload);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.providerShipmentId()).isEqualTo("label-1");
        assertThat(response.body().path("id").asText()).isEqualTo("label-1");
        server.verify();
    }

    @Test
    void treatsAnUnparseableSuccessfulCartResponseAsUnknown() {
        server.expect(requestTo(CART_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"unexpected\":\"shape\"}"));

        assertThatThrownBy(() -> client.addToCart(new ObjectMapper().createObjectNode()))
                .isInstanceOf(ShippingProviderOperationException.class)
                .hasMessage("shipping provider outcome is unknown");
        server.verify();
    }

    @Test
    void purchasesAndGeneratesOneExplicitKnownProviderIdPerRequest() {
        server.expect(requestTo(CHECKOUT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"orders\":[\"label-1\"]}"))
                .andRespond(withSuccess("{\"result\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(GENERATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"orders\":[\"label-2\"]}"))
                .andRespond(withSuccess("{\"result\":true}", MediaType.APPLICATION_JSON));

        assertThat(client.purchase(List.of("label-1")).body().path("result").asBoolean())
                .isTrue();
        assertThat(client.generate(List.of("label-2")).body().path("result").asBoolean())
                .isTrue();
        server.verify();
    }

    @Test
    void marksOnlyDocumentedValidationRejectionAsDefinitiveAndSanitizesProviderBody() {
        server.expect(requestTo(CART_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("sensitive provider response including address and token"));

        assertThatThrownBy(() -> client.addToCart(new ObjectMapper().createObjectNode()))
                .isInstanceOf(ShippingProviderOperationException.class)
                .hasMessage("shipping provider rejected the request")
                .extracting("certainty")
                .isEqualTo(ShippingProviderOperationException.Certainty.DEFINITIVE_FAILURE);
        server.verify();
    }

    @Test
    void treatsOtherHttpErrorsAsUnknownBecauseTheProviderDoesNotPromiseWriteSemantics() {
        server.expect(requestTo(CHECKOUT_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY)
                        .body("private provider response"));

        assertThatThrownBy(() -> client.purchase(List.of("label-1")))
                .isInstanceOf(ShippingProviderOperationException.class)
                .hasMessage("shipping provider outcome is unknown")
                .extracting("certainty")
                .isEqualTo(ShippingProviderOperationException.Certainty.UNKNOWN);
        server.verify();
    }

    @Test
    void rejectsEmptyBatchedOrRepeatedProviderIdsBeforeSending() {
        assertThatThrownBy(() -> client.purchase(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider shipment ids are invalid");
        assertThatThrownBy(() -> client.purchase(List.of("label-1", "label-2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider shipment ids are invalid");
        assertThatThrownBy(() -> client.generate(List.of("label-1", "label-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider shipment ids are invalid");
        server.verify();
    }

    @Test
    void rejectsUserAgentWithoutAValidContactEmail() {
        assertThatThrownBy(() -> new MelhorEnvioLabelClient(
                        RestClient.builder(), new ObjectMapper(), "sandbox-token", "De La do Pará (@)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("shipping provider configuration is invalid");
    }
}
