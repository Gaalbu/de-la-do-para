package br.com.deladopara.shipping.adapter;

import br.com.deladopara.shipping.application.CarrierQuote;
import br.com.deladopara.shipping.application.FreightQuoteAdapter;
import br.com.deladopara.shipping.application.ShippingAdapterException;
import br.com.deladopara.shipping.application.ShippingQuoteRequest;
import br.com.deladopara.shipping.domain.PackageComposer.PackagePlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/** Parses the sanitized sandbox contract; it deliberately performs no network call. */
public final class MelhorEnvioSandboxAdapter implements FreightQuoteAdapter {

    private final ObjectMapper objectMapper;

    public MelhorEnvioSandboxAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public CarrierQuote quote(ShippingQuoteRequest request, String sandboxPayload, Instant now) {
        if (sandboxPayload == null || sandboxPayload.isBlank()) {
            throw new ShippingAdapterException("sandbox response is empty");
        }
        try {
            var root = objectMapper.readTree(sandboxPayload);
            var quote = root.path("quote");
            var packageSequences = quote.path("package_sequences");
            var sequences = parseSequences(packageSequences);
            validateCoverage(request.packages(), sequences);
            var expiresAt = Instant.parse(requiredText(quote, "expires_at"));
            if (!expiresAt.isAfter(now)) {
                throw new ShippingAdapterException("carrier quote is expired");
            }
            var priceCents = quote.path("price_cents").asLong(-1);
            var deliveryDays = quote.path("delivery_days").asInt(-1);
            if (priceCents < 0 || deliveryDays < 1) {
                throw new ShippingAdapterException("carrier quote cost or deadline is invalid");
            }
            return new CarrierQuote(
                    requiredText(quote, "service_id"),
                    requiredText(quote, "service_name"),
                    priceCents,
                    deliveryDays,
                    expiresAt,
                    sequences);
        } catch (ShippingAdapterException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ShippingAdapterException("carrier response is invalid");
        }
    }

    private static List<Integer> parseSequences(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            throw new ShippingAdapterException("carrier package coverage is missing");
        }
        var sequences = new java.util.ArrayList<Integer>();
        node.forEach(value -> sequences.add(value.asInt(-1)));
        return List.copyOf(sequences);
    }

    private static void validateCoverage(List<PackagePlan> packages, List<Integer> sequences) {
        var expected = packages.stream().map(PackagePlan::sequence).toList();
        if (sequences.size() != expected.size()
                || !new HashSet<>(sequences).equals(new HashSet<>(expected))
                || new HashSet<>(sequences).size() != sequences.size()) {
            throw new ShippingAdapterException("carrier package coverage is incomplete");
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
