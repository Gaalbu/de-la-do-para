package br.com.deladopara.shipping.application;

import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import br.com.deladopara.catalog.domain.Product;
import br.com.deladopara.pricing.application.SkuPriceService;
import br.com.deladopara.shipping.domain.PackageComposer;
import br.com.deladopara.shipping.domain.PackageComposer.Category;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ShippingQuoteGenerationService {

    private final ProductSkuRepository skus;
    private final SkuPriceService prices;
    private final FreightQuoteAdapter adapter;
    private final ShippingQuoteService quoteService;
    private final DeliveryOptionsService deliveryOptions;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String originPostalCode;

    public ShippingQuoteGenerationService(
            ProductSkuRepository skus,
            SkuPriceService prices,
            ObjectProvider<FreightQuoteAdapter> adapter,
            ShippingQuoteService quoteService,
            DeliveryOptionsService deliveryOptions,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${shipping.adapter.origin-postal-code:66053000}") String originPostalCode) {
        this.skus = skus;
        this.prices = prices;
        this.adapter = adapter.getIfAvailable();
        this.quoteService = quoteService;
        this.deliveryOptions = deliveryOptions;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.originPostalCode = originPostalCode;
    }

    public List<ShippingQuote> findOrCreate(
            UUID snapshotId, long snapshotVersion, String serializedItems, String destinationPostalCode) {
        var input = currentInput(snapshotId, snapshotVersion, serializedItems, destinationPostalCode);
        var destination = input.destination();
        var fingerprint = input.fingerprint();
        var available = deliveryOptions.findAvailable(snapshotId, snapshotVersion, destination);
        var matching = available.stream()
                .filter(quote -> quote.inputFingerprint().equals(fingerprint))
                .toList();
        if (!matching.isEmpty() || adapter == null) {
            return matching.isEmpty() ? available : matching;
        }
        var request =
                new ShippingQuoteRequest(originPostalCode, destination, input.packages(), input.insuranceValues());
        var carrierQuotes = adapter.quote(request, Instant.now(clock));
        return quoteService.persistAll(
                snapshotId, snapshotVersion, destination, fingerprint, carrierQuotes, 1);
    }

    public void assertCurrent(UUID snapshotId, long snapshotVersion, String serializedItems, ShippingQuote quote) {
        var current = currentInput(snapshotId, snapshotVersion, serializedItems, quote.destinationPostalCode());
        if (!quote.inputFingerprint().equals(current.fingerprint())) {
            throw new DeliveryOptionsService.SelectionConflictException();
        }
    }

    private CurrentInput currentInput(
            UUID snapshotId, long snapshotVersion, String serializedItems, String destinationPostalCode) {
        var destination = normalizePostalCode(destinationPostalCode);
        var items = readItems(serializedItems);
        var skuIds = items.stream().map(SnapshotItem::skuId).distinct().toList();
        var skuById =
                skus.findAllByIdIn(skuIds).stream().collect(Collectors.toMap(sku -> sku.getId(), Function.identity()));
        if (skuById.size() != skuIds.size() || skuById.values().stream().anyMatch(sku -> !sku.isActive())) {
            throw new IllegalStateException("checkout snapshot contains unavailable shipping items");
        }
        var currentPrices = prices.findCurrentPrices(skuIds);
        if (!currentPrices.keySet().containsAll(skuIds)) {
            throw new IllegalStateException("checkout snapshot is missing current item prices");
        }
        var lines = items.stream()
                .map(item -> {
                    var sku = skuById.get(item.skuId());
                    return new PackageComposer.Line(
                            sku.getId(),
                            sku.getProductCategory() == Product.Category.FOOD ? Category.FOOD : Category.CRAFT,
                            sku.isFragile(),
                            item.quantity(),
                            sku.getLengthMm(),
                            sku.getWidthMm(),
                            sku.getHeightMm(),
                            sku.getGrossWeightGrams());
                })
                .toList();
        var packages = PackageComposer.compose(lines);
        var insuranceValues = packages.stream()
                .collect(Collectors.toUnmodifiableMap(
                        PackageComposer.PackagePlan::sequence,
                        packagePlan -> packagePlan.lines().stream()
                                .mapToLong(line -> Math.multiplyExact(
                                        currentPrices
                                                .get(line.skuId())
                                                .unitPrice()
                                                .cents(),
                                        line.quantity()))
                                .reduce(0L, Math::addExact)));
        var fingerprint = fingerprint(snapshotId, snapshotVersion, destination, packages, insuranceValues);
        return new CurrentInput(destination, packages, insuranceValues, fingerprint);
    }

    private List<SnapshotItem> readItems(String serializedItems) {
        try {
            var items = objectMapper.readValue(serializedItems, new TypeReference<List<SnapshotItem>>() {});
            if (items.isEmpty() || items.stream().anyMatch(item -> item.skuId() == null || item.quantity() <= 0)) {
                throw new IllegalStateException("checkout snapshot items are invalid");
            }
            return items;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("checkout snapshot items are invalid", exception);
        }
    }

    private String fingerprint(
            UUID snapshotId,
            long snapshotVersion,
            String destination,
            List<PackageComposer.PackagePlan> packages,
            Map<Integer, Long> insuranceValues) {
        try {
            var shapes = packages.stream()
                    .map(packagePlan -> new PackageFingerprint(
                            packagePlan.sequence(),
                            packagePlan.category(),
                            packagePlan.fragile(),
                            packagePlan.lines(),
                            packagePlan.lengthMm(),
                            packagePlan.widthMm(),
                            packagePlan.heightMm(),
                            packagePlan.totalWeightGrams(),
                            insuranceValues.get(packagePlan.sequence())))
                    .toList();
            var canonical = objectMapper.writeValueAsBytes(new FingerprintInput(
                    snapshotId, snapshotVersion, originPostalCode, destination, shapes));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception exception) {
            throw new IllegalStateException("shipping quote fingerprint could not be generated", exception);
        }
    }

    private static String normalizePostalCode(String postalCode) {
        if (postalCode == null) {
            throw new IllegalArgumentException("destination postal code is required");
        }
        var normalized = postalCode.replace("-", "").trim();
        if (!normalized.matches("\\d{8}")) {
            throw new IllegalArgumentException("destination postal code is invalid");
        }
        return normalized;
    }

    private record SnapshotItem(UUID skuId, int quantity) {}

    private record CurrentInput(
            String destination,
            List<PackageComposer.PackagePlan> packages,
            Map<Integer, Long> insuranceValues,
            String fingerprint) {}

    private record FingerprintInput(
            UUID snapshotId,
            long snapshotVersion,
            String origin,
            String destination,
            List<PackageFingerprint> packages) {}

    private record PackageFingerprint(
            int sequence,
            Category category,
            boolean fragile,
            List<PackageComposer.Line> lines,
            int lengthMm,
            int widthMm,
            int heightMm,
            int weightGrams,
            long insuranceValueCents) {}
}
