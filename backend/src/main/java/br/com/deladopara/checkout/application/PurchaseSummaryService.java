package br.com.deladopara.checkout.application;

import br.com.deladopara.cart.application.GuestCartService;
import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import br.com.deladopara.checkout.adapter.persistence.CheckoutSnapshotEntity;
import br.com.deladopara.checkout.adapter.persistence.CheckoutSnapshotRepository;
import br.com.deladopara.checkout.application.CheckoutSnapshotService.SnapshotVersionConflictException;
import br.com.deladopara.pricing.application.CouponReservationService;
import br.com.deladopara.pricing.application.SkuPriceService;
import br.com.deladopara.pricing.domain.CouponDiscount;
import br.com.deladopara.pricing.domain.PurchaseLine;
import br.com.deladopara.pricing.domain.PurchaseTotalCalculator;
import br.com.deladopara.pricing.domain.PurchaseTotalRequest;
import br.com.deladopara.shipping.application.DeliveryOptionsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side purchase summary (SPEC-checkout §A3): current prices, the selected delivery quote or pickup option and
 * a coupon preview, with a {@code summaryVersion} that the purchase must echo back unchanged.
 */
@Service
public class PurchaseSummaryService {

    public static final String PRICING_RULE_VERSION = "pricing-v1";

    private final CheckoutSnapshotRepository snapshots;
    private final ProductSkuRepository skus;
    private final SkuPriceService prices;
    private final DeliveryOptionsService deliveryOptions;
    private final PickupOptionsService pickupOptions;
    private final CouponReservationService coupons;
    private final ObjectMapper objectMapper;
    private final PurchaseTotalCalculator calculator = new PurchaseTotalCalculator();

    public PurchaseSummaryService(
            CheckoutSnapshotRepository snapshots,
            ProductSkuRepository skus,
            SkuPriceService prices,
            DeliveryOptionsService deliveryOptions,
            PickupOptionsService pickupOptions,
            CouponReservationService coupons,
            ObjectMapper objectMapper) {
        this.snapshots = snapshots;
        this.skus = skus;
        this.prices = prices;
        this.deliveryOptions = deliveryOptions;
        this.pickupOptions = pickupOptions;
        this.coupons = coupons;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PurchaseSummary summarize(String sessionId, UUID snapshotId, long snapshotVersion, Selection selection) {
        return summarize(ownedSnapshot(sessionId, snapshotId, snapshotVersion), selection);
    }

    CheckoutSnapshotEntity ownedSnapshot(String sessionId, UUID snapshotId, long snapshotVersion) {
        var snapshot = snapshots
                .findByIdAndGuestSessionKey(snapshotId, GuestCartService.hashSession(sessionId))
                .orElseThrow(SnapshotNotFoundException::new);
        if (snapshot.getCartVersion() != snapshotVersion) {
            throw new SnapshotVersionConflictException();
        }
        return snapshot;
    }

    PurchaseSummary summarize(CheckoutSnapshotEntity snapshot, Selection selection) {
        var items = items(snapshot.getItems()).stream()
                .sorted(Comparator.comparing(CheckoutSnapshotService.Item::skuId))
                .toList();
        var current = prices.findCurrentPrices(
                items.stream().map(CheckoutSnapshotService.Item::skuId).toList());
        var lines = new ArrayList<Line>();
        for (var item : items) {
            var sku = skus.findById(item.skuId())
                    .filter(candidate -> candidate.isActive())
                    .orElseThrow(() -> new ItemUnavailableException(item.skuId()));
            var price = current.get(item.skuId());
            if (price == null) {
                throw new ItemUnavailableException(item.skuId());
            }
            var unit = price.unitPrice().cents();
            lines.add(new Line(
                    item.skuId(),
                    sku.getProduct().getDisplayName(),
                    sku.getSalesUnit(),
                    item.quantity(),
                    unit,
                    Math.multiplyExact(unit, item.quantity())));
        }
        Fulfillment fulfillment;
        if (selection.mode() == Mode.DELIVERY) {
            var quote = deliveryOptions.select(
                    snapshot.getId(), snapshot.getCartVersion(), selection.quoteId(), selection.inputFingerprint());
            fulfillment = new Fulfillment(
                    Mode.DELIVERY,
                    quote.id().toString(),
                    quote.serviceName(),
                    quote.destinationPostalCode(),
                    quote.priceCents(),
                    quote.preparationDays(),
                    quote.deliveryDays());
        } else {
            var option = pickupOptions.select(snapshot.getItems(), selection.pickupOptionId());
            fulfillment = new Fulfillment(
                    Mode.PICKUP, option.id(), option.point(), option.window(), 0, option.preparationDays(), null);
        }
        var couponCode =
                selection.couponCode() == null || selection.couponCode().isBlank()
                        ? null
                        : CouponReservationService.normalizeCode(selection.couponCode());
        CouponDiscount discount = couponCode == null
                ? null
                : coupons.previewDiscount(couponCode).orElseThrow(CouponUnavailableException::new);
        var total = calculator.calculate(new PurchaseTotalRequest(
                lines.stream()
                        .map(line -> new PurchaseLine(line.skuId().toString(), line.unitPriceCents(), line.quantity()))
                        .toList(),
                fulfillment.shippingCents(),
                discount));
        var summary = new PurchaseSummary(
                snapshot.getId(),
                snapshot.getCartVersion(),
                List.copyOf(lines),
                fulfillment,
                couponCode,
                discount,
                total.subtotalCents(),
                total.shippingCents(),
                total.discountCents(),
                total.totalCents(),
                null);
        return summary.withVersion(version(summary));
    }

    private List<CheckoutSnapshotService.Item> items(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<CheckoutSnapshotService.Item>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("checkout snapshot items are invalid", exception);
        }
    }

    private static String version(PurchaseSummary summary) {
        var canonical = new StringBuilder(PRICING_RULE_VERSION);
        for (var line : summary.lines()) {
            canonical
                    .append('|')
                    .append(line.skuId())
                    .append(':')
                    .append(line.quantity())
                    .append(':')
                    .append(line.unitPriceCents());
        }
        var f = summary.fulfillment();
        canonical
                .append('|')
                .append(f.mode())
                .append(':')
                .append(f.optionId())
                .append(':')
                .append(f.shippingCents())
                .append(':')
                .append(f.preparationDays())
                .append(':')
                .append(f.deliveryDays());
        canonical.append('|').append(summary.couponCode()).append(':').append(summary.discountCents());
        canonical.append('|').append(summary.totalCents());
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public enum Mode {
        DELIVERY,
        PICKUP
    }

    public record Selection(
            Mode mode, UUID quoteId, String inputFingerprint, String pickupOptionId, String couponCode) {

        public Selection {
            if (mode == null
                    || (mode == Mode.DELIVERY && (quoteId == null || inputFingerprint == null))
                    || (mode == Mode.PICKUP && (pickupOptionId == null || pickupOptionId.isBlank()))) {
                throw new InvalidSelectionException();
            }
        }
    }

    public record Line(
            UUID skuId, String productName, String salesUnit, int quantity, long unitPriceCents, long lineTotalCents) {}

    /**
     * For delivery, {@code label} is the carrier service and {@code detail} the destination postal code; for pickup
     * they are the pickup point and its opening window.
     */
    public record Fulfillment(
            Mode mode,
            String optionId,
            String label,
            String detail,
            long shippingCents,
            int preparationDays,
            Integer deliveryDays) {}

    public record PurchaseSummary(
            UUID snapshotId,
            long snapshotVersion,
            List<Line> lines,
            Fulfillment fulfillment,
            String couponCode,
            CouponDiscount discount,
            long subtotalCents,
            long shippingCents,
            long discountCents,
            long totalCents,
            String summaryVersion) {

        PurchaseSummary withVersion(String version) {
            return new PurchaseSummary(
                    snapshotId,
                    snapshotVersion,
                    lines,
                    fulfillment,
                    couponCode,
                    discount,
                    subtotalCents,
                    shippingCents,
                    discountCents,
                    totalCents,
                    version);
        }
    }

    public static class SnapshotNotFoundException extends RuntimeException {}

    public static class InvalidSelectionException extends RuntimeException {}

    public static class CouponUnavailableException extends RuntimeException {}

    public static class ItemUnavailableException extends RuntimeException {

        private final UUID skuId;

        public ItemUnavailableException(UUID skuId) {
            this.skuId = skuId;
        }

        public UUID skuId() {
            return skuId;
        }
    }
}
