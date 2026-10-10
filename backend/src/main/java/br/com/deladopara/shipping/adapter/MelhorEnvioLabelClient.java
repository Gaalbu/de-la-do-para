package br.com.deladopara.shipping.adapter;

import br.com.deladopara.shipping.application.ShippingProviderOperationException;
import br.com.deladopara.shipping.application.ShippingProviderOperationException.Certainty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Sends label writes only to the Melhor Envio sandbox. */
public final class MelhorEnvioLabelClient {

    private static final String API_BASE = "https://sandbox.melhorenvio.com.br/api/v2/me";
    private static final String CART_PATH = "/cart";
    private static final String CHECKOUT_PATH = "/shipment/checkout";
    private static final String GENERATE_PATH = "/shipment/generate";
    private static final String TRACKING_PATH = "/shipment/tracking";
    private static final Pattern USER_AGENT_EMAIL =
            Pattern.compile(".*[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}.*", Pattern.CASE_INSENSITIVE);

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public MelhorEnvioLabelClient(
            RestClient.Builder builder, ObjectMapper objectMapper, String credential, String userAgent) {
        if (builder == null
                || objectMapper == null
                || credential == null
                || credential.isBlank()
                || credential.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("shipping provider configuration is invalid");
        }
        if (userAgent == null
                || userAgent.isBlank()
                || userAgent.contains("\r")
                || userAgent.contains("\n")
                || !USER_AGENT_EMAIL.matcher(userAgent).matches()) {
            throw new IllegalArgumentException("shipping provider configuration is invalid");
        }
        this.objectMapper = objectMapper;
        client = builder.baseUrl(API_BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public ProviderResponse addToCart(JsonNode acceptedShipmentSnapshot) {
        if (acceptedShipmentSnapshot == null || !acceptedShipmentSnapshot.isObject()) {
            throw new IllegalArgumentException("accepted shipment snapshot is invalid");
        }
        var response = post(CART_PATH, acceptedShipmentSnapshot, 201);
        var id = response.body() == null ? null : response.body().path("id");
        if (id == null || !id.isTextual() || id.asText().isBlank()) {
            throw new ShippingProviderOperationException(Certainty.UNKNOWN);
        }
        return new ProviderResponse(response.statusCode(), response.body(), id.asText());
    }

    public ProviderResponse purchase(List<String> providerShipmentIds) {
        return post(CHECKOUT_PATH, ordersBody(providerShipmentIds), 200);
    }

    public ProviderResponse generate(List<String> providerShipmentIds) {
        return post(GENERATE_PATH, ordersBody(providerShipmentIds), 200);
    }

    public ProviderResponse tracking(String providerShipmentId) {
        return post(TRACKING_PATH, ordersBody(List.of(providerShipmentId)), 200);
    }

    private ProviderResponse post(String path, Object body, int expectedStatus) {
        try {
            var requestBody = objectMapper.writeValueAsString(body);
            ResponseEntity<String> response = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, result) -> {
                        var certainty = result.getStatusCode().value() == 422
                                ? Certainty.DEFINITIVE_FAILURE
                                : Certainty.UNKNOWN;
                        throw new ShippingProviderOperationException(certainty);
                    })
                    .toEntity(String.class);
            if (response.getStatusCode().value() != expectedStatus) {
                throw new ShippingProviderOperationException(Certainty.UNKNOWN);
            }
            var responseBody = response.getBody();
            var parsedBody =
                    responseBody == null || responseBody.isBlank() ? null : objectMapper.readTree(responseBody);
            return new ProviderResponse(response.getStatusCode().value(), parsedBody, null);
        } catch (ShippingProviderOperationException exception) {
            throw exception;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ShippingProviderOperationException(Certainty.UNKNOWN);
        } catch (RestClientException exception) {
            throw new ShippingProviderOperationException(Certainty.UNKNOWN);
        }
    }

    private static Object ordersBody(List<String> ids) {
        if (ids == null || ids.size() != 1 || ids.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("provider shipment ids are invalid");
        }
        return Map.of("orders", List.copyOf(ids));
    }

    public record ProviderResponse(int statusCode, JsonNode body, String providerShipmentId) {}
}
