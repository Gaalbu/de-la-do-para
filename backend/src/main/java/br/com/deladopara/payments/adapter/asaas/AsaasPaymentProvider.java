package br.com.deladopara.payments.adapter.asaas;

import br.com.deladopara.payments.application.PaymentProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Hosted Asaas checkout (Pix or card) for the exact order total. The payment intent id goes as
 * {@code externalReference}; Asaas does not document it as unique nor as an idempotency key, so a lost response is
 * never retried here (SPEC-payments PAY-006). Behaviour is proven against recorded HTTP responses; sandbox evidence
 * stays in the opt-in homologation script.
 */
public class AsaasPaymentProvider implements PaymentProvider {

    static final String ITEM_NAME = "Pedido De Lá do Pará";

    /** 8×8 terracotta square: Asaas requires an item image. */
    static final String ITEM_IMAGE =
            "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAEUlEQVR42mM4EK2HFTEMLQkASChSQZPgLRYAAAAASUVORK5CYII=";

    /** Statuses of an Asaas payment (cobrança) that mean the buyer paid. */
    private static final Set<String> PAID_STATUSES = Set.of("CONFIRMED", "RECEIVED", "RECEIVED_IN_CASH");

    private final RestClient http;
    private final AsaasProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public AsaasPaymentProvider(RestClient.Builder http, AsaasProperties properties, ObjectMapper json, Clock clock) {
        this.http = http.baseUrl(properties.baseUrl().toString())
                .defaultHeader("access_token", properties.apiKey())
                .defaultHeader("User-Agent", "de-la-do-para")
                .build();
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public CreatedCheckout createCheckout(CheckoutRequest request) {
        var requestedAt = clock.instant();
        var minutes = minutesToExpire(request, requestedAt);
        var body = exchange(
                http.post()
                        .uri("/v3/checkouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(write(checkoutBody(request, minutes))),
                true);
        var link = URI.create(text(body, "link"));
        if (!"https".equals(link.getScheme()) || !properties.checkoutHosts().contains(link.getHost())) {
            throw new AsaasUnknownOutcomeException("checkout link host is not allowed");
        }
        return new CreatedCheckout(text(body, "id"), link.toString(), requestedAt.plus(Duration.ofMinutes(minutes)));
    }

    /**
     * The link never outlives the stock hold: it is shortened to the whole minutes left before {@code payBy}, counted
     * from the latest moment Asaas may receive the request. Below Asaas' 10-minute minimum the request is refused
     * before any call, instead of selling stock that is no longer held.
     */
    private int minutesToExpire(CheckoutRequest request, Instant requestedAt) {
        var left = Duration.between(requestedAt.plus(properties.timeout()), request.payBy())
                .toMinutes();
        var minutes = (int) Math.min(properties.minutesToExpire(), left);
        if (minutes < AsaasProperties.MIN_MINUTES) {
            throw new ProviderRejectedException("RESERVATION_TOO_SHORT");
        }
        return minutes;
    }

    /**
     * Reads the payment Asaas creates when the buyer pays. Empty only means no payment carries our reference yet: it
     * does not prove the checkout was never created, so it must not be used to retry a lost creation.
     */
    @Override
    public Optional<CheckoutState> findCheckout(UUID paymentIntentId) {
        var uri = UriComponentsBuilder.fromPath("/v3/payments")
                .queryParam("externalReference", paymentIntentId)
                .build()
                .toUriString();
        var payments = exchange(http.get().uri(uri), false).path("data");
        JsonNode chosen = null;
        for (var payment : payments) {
            if (chosen == null || PAID_STATUSES.contains(payment.path("status").asText())) {
                chosen = payment;
            }
        }
        if (chosen == null) {
            return Optional.empty();
        }
        var status =
                PAID_STATUSES.contains(chosen.path("status").asText()) ? CheckoutStatus.PAID : CheckoutStatus.PENDING;
        return Optional.of(new CheckoutState(text(chosen, "checkoutSession"), status, cents(chosen.path("value"))));
    }

    private Map<String, Object> checkoutBody(CheckoutRequest request, int minutes) {
        var orderUrl = UriComponentsBuilder.fromUri(properties.storeUrl())
                .path("/orders/{orderId}")
                .buildAndExpand(request.orderId())
                .toUriString();
        return Map.of(
                "billingTypes", List.of("PIX", "CREDIT_CARD"),
                "chargeTypes", List.of("DETACHED"),
                "minutesToExpire", minutes,
                "externalReference", request.paymentIntentId().toString(),
                "callback", Map.of("successUrl", orderUrl, "cancelUrl", orderUrl, "expiredUrl", orderUrl),
                "items",
                        List.of(Map.of(
                                "name",
                                ITEM_NAME,
                                "description",
                                "Pedido " + request.orderId(),
                                "quantity",
                                1,
                                "value",
                                BigDecimal.valueOf(request.amountCents(), 2),
                                "imageBase64",
                                ITEM_IMAGE)));
    }

    /**
     * A client error means Asaas refused the request before acting; anything else after sending (server error,
     * timeout, unreadable body) leaves the outcome unknown.
     */
    private JsonNode exchange(RestClient.RequestHeadersSpec<?> request, boolean creating) {
        try {
            return request.exchange((req, response) -> {
                var status = response.getStatusCode();
                if (creating && isRejection(status)) {
                    throw new ProviderRejectedException("HTTP_" + status.value());
                }
                if (!status.is2xxSuccessful()) {
                    throw new AsaasUnknownOutcomeException("Asaas answered HTTP " + status.value());
                }
                return json.readTree(response.getBody());
            });
        } catch (ProviderRejectedException | AsaasUnknownOutcomeException known) {
            throw known;
        } catch (RuntimeException failure) {
            throw new AsaasUnknownOutcomeException(
                    "Asaas call failed: " + failure.getClass().getSimpleName());
        }
    }

    private static boolean isRejection(HttpStatusCode status) {
        return status.is4xxClientError() && status.value() != 408 && status.value() != 409;
    }

    private String write(Map<String, Object> body) {
        try {
            return json.writeValueAsString(body);
        } catch (IOException impossible) {
            throw new IllegalStateException("Asaas request could not be serialized", impossible);
        }
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new AsaasUnknownOutcomeException("Asaas response has no " + field);
        }
        return value;
    }

    private static long cents(JsonNode value) {
        if (!value.isNumber()) {
            throw new AsaasUnknownOutcomeException("Asaas response has no value");
        }
        return value.decimalValue().movePointRight(2).longValueExact();
    }

    /** The provider may or may not have acted; the caller records UNKNOWN and reconciles. */
    public static class AsaasUnknownOutcomeException extends RuntimeException {

        public AsaasUnknownOutcomeException(String message) {
            super(message);
        }
    }
}
