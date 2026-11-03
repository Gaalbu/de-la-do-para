package br.com.deladopara.shipping.adapter;

import br.com.deladopara.shipping.application.CarrierQuote;
import br.com.deladopara.shipping.application.FreightQuoteAdapter;
import br.com.deladopara.shipping.application.ShippingAdapterException;
import br.com.deladopara.shipping.application.ShippingQuoteRequest;
import br.com.deladopara.shipping.domain.PackageComposer.PackagePlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Parses Melhor Envio's documented freight calculation response. */
public final class MelhorEnvioSandboxAdapter implements FreightQuoteAdapter {

    private final ObjectMapper objectMapper;
    private final RestClient client;
    private final ShippingAdapterProperties properties;

    public MelhorEnvioSandboxAdapter(
            RestClient.Builder builder, ObjectMapper objectMapper, ShippingAdapterProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.client = builder.baseUrl(properties.baseUrl() + "/api/v2/me")
                .defaultHeader("Authorization", "Bearer " + properties.credential())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("User-Agent", properties.userAgent())
                .build();
    }

    @Override
    public List<CarrierQuote> quote(ShippingQuoteRequest request, Instant now) {
        try {
            var sandboxPayload = client.post()
                    .uri("/shipment/calculate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", Map.of("postal_code", properties.originPostalCode()),
                            "to",
                                    Map.of(
                                            "postal_code",
                                            request.destinationPostalCode().replace("-", "")),
                            "volumes",
                                    request.packages().stream()
                                            .map(p -> Map.of(
                                                    "length",
                                                    p.lengthMm() / 10.0,
                                                    "width",
                                                    p.widthMm() / 10.0,
                                                    "height",
                                                    p.heightMm() / 10.0,
                                                    "weight",
                                                    p.totalWeightGrams() / 1000.0,
                                                    "insurance",
                                                    request.insuranceValueCentsByPackage()
                                                                    .get(p.sequence())
                                                            / 100.0))
                                            .toList(),
                            "options", Map.of("receipt", false, "own_hand", false)))
                    .retrieve()
                    .body(String.class);
            if (sandboxPayload == null || sandboxPayload.isBlank()) {
                throw new ShippingAdapterException("carrier response is empty");
            }
            var root = objectMapper.readTree(sandboxPayload);
            if (!root.isArray()) {
                throw new ShippingAdapterException("carrier response is not an options array");
            }
            var quotes = new ArrayList<CarrierQuote>();
            for (var option : root) {
                var packages = option.path("packages");
                if (!packages.isArray() || packages.isEmpty()) {
                    continue;
                }
                validatePackageCoverage(request, packages);
                var serviceId = requiredText(option, "id");
                var serviceName = requiredText(option, "name");
                var priceCents = moneyToCents(requiredText(option, "custom_price"));
                var deliveryDays = option.path("custom_delivery_time").asInt(-1);
                if (priceCents <= 0 || deliveryDays < 1) {
                    continue;
                }
                quotes.add(new CarrierQuote(
                        serviceId,
                        serviceName,
                        priceCents,
                        deliveryDays,
                        now.plus(properties.quoteValidity()),
                        request.packages().stream().map(PackagePlan::sequence).toList()));
            }
            return List.copyOf(quotes);
        } catch (ShippingAdapterException exception) {
            throw exception;
        } catch (org.springframework.web.client.RestClientException exception) {
            throw new ShippingAdapterException("carrier quote request failed");
        } catch (Exception exception) {
            throw new ShippingAdapterException("carrier response is invalid");
        }
    }

    private static void validatePackageCoverage(ShippingQuoteRequest request, JsonNode actual) {
        var expected = request.packages();
        if (actual.size() != expected.size()) {
            throw new ShippingAdapterException("carrier package coverage is incomplete");
        }
        var expectedShapes = expected.stream()
                .map(p -> List.of(
                        p.lengthMm(),
                        p.widthMm(),
                        p.heightMm(),
                        p.totalWeightGrams(),
                        Math.toIntExact(request.insuranceValueCentsByPackage().get(p.sequence()))))
                .sorted(java.util.Comparator.comparing(Object::toString))
                .toList();
        var actualShapes = new ArrayList<List<Integer>>();
        actual.forEach(p -> {
            var dims = p.path("dimensions");
            actualShapes.add(List.of(
                    (int) Math.round(dims.path("length").asDouble(-1) * 10),
                    (int) Math.round(dims.path("width").asDouble(-1) * 10),
                    (int) Math.round(dims.path("height").asDouble(-1) * 10),
                    (int) Math.round(new BigDecimal(p.path("weight").asText("-1")).doubleValue() * 1000),
                    (int) Math.round(new BigDecimal(p.path("insurance_value").asText("0")).doubleValue() * 100)));
        });
        var sortedActual = actualShapes.stream()
                .sorted(java.util.Comparator.comparing(Object::toString))
                .toList();
        if (!expectedShapes.equals(sortedActual)) {
            throw new ShippingAdapterException("carrier package data does not match request");
        }
    }

    private static long moneyToCents(String amount) {
        try {
            return new BigDecimal(amount)
                    .movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new ShippingAdapterException("carrier quote price is invalid");
        }
    }

    private static String requiredText(JsonNode node, String field) {
        var value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new ShippingAdapterException("carrier response field is missing: " + field);
        }
        return value;
    }
}
