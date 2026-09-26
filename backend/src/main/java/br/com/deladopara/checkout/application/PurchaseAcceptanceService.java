package br.com.deladopara.checkout.application;

import br.com.deladopara.cart.application.GuestCartService;
import br.com.deladopara.checkout.application.CheckoutIdempotency.Outcome;
import br.com.deladopara.checkout.application.PurchaseSummaryService.Mode;
import br.com.deladopara.checkout.application.PurchaseSummaryService.PurchaseSummary;
import br.com.deladopara.checkout.application.PurchaseSummaryService.Selection;
import br.com.deladopara.identity.application.AccountService;
import br.com.deladopara.inventory.application.StockReservationService;
import br.com.deladopara.orders.application.CreateOrderCommand;
import br.com.deladopara.orders.application.OrderAccessTokens;
import br.com.deladopara.orders.application.OrderQueryService;
import br.com.deladopara.orders.application.OrderService;
import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.payments.application.PaymentIntentService;
import br.com.deladopara.pricing.application.CouponReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts a purchase in one PostgreSQL transaction (SPEC-checkout §A4): idempotency claim, summary check, order,
 * stock reservation, coupon reservation, guest access token, payment intent and cart consumption. Any failure rolls
 * all of it back; no HTTP call or broker send happens here.
 */
@Service
public class PurchaseAcceptanceService {

    private static final ZoneId BELEM = ZoneId.of("America/Belem");

    private final CheckoutIdempotency idempotency;
    private final PurchaseSummaryService summaries;
    private final OrderService orders;
    private final OrderQueryService orderQueries;
    private final OrderAccessTokens accessTokens;
    private final StockReservationService stock;
    private final CouponReservationService coupons;
    private final PaymentIntentService payments;
    private final GuestCartService carts;
    private final AccountService accounts;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PurchaseAcceptanceService(
            CheckoutIdempotency idempotency,
            PurchaseSummaryService summaries,
            OrderService orders,
            OrderQueryService orderQueries,
            OrderAccessTokens accessTokens,
            StockReservationService stock,
            CouponReservationService coupons,
            PaymentIntentService payments,
            GuestCartService carts,
            AccountService accounts,
            ObjectMapper objectMapper,
            Clock clock) {
        this.idempotency = idempotency;
        this.summaries = summaries;
        this.orders = orders;
        this.orderQueries = orderQueries;
        this.accessTokens = accessTokens;
        this.stock = stock;
        this.coupons = coupons;
        this.payments = payments;
        this.carts = carts;
        this.accounts = accounts;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AcceptedPurchase accept(PurchaseCommand command) {
        var buyer = command.accountEmail() == null
                ? null
                : accounts.buyer(command.accountEmail())
                        .orElseThrow(PurchaseSummaryService.SnapshotNotFoundException::new);
        var email = buyer != null ? buyer.email() : normalizedEmail(command.guestEmail());
        var subject = buyer != null
                ? "account:" + buyer.accountId()
                : "guest:" + GuestCartService.hashSession(command.sessionId());
        var requestHash = CheckoutIdempotency.purchaseHash(
                command.snapshotId(),
                command.snapshotVersion(),
                canonicalFulfillment(command),
                email,
                command.selection().couponCode(),
                command.summaryVersion());
        var claim = idempotency.claim(subject, CheckoutIdempotency.PURCHASE, command.idempotencyKey(), requestHash);
        if (claim.outcome() == Outcome.REPLAY) {
            return replay(claim.orderId());
        }

        var snapshot = summaries.ownedSnapshot(command.sessionId(), command.snapshotId(), command.snapshotVersion());
        var summary = summaries.summarize(snapshot, command.selection());
        if (!summary.summaryVersion().equals(command.summaryVersion())) {
            throw new SummaryChangedException(summary);
        }
        if (summary.totalCents() <= 0) {
            throw new ZeroTotalException();
        }
        var correlationId = UUID.randomUUID();
        var created = orders.create(orderCommand(subject, command, buyer, email, summary, correlationId));
        var orderId = created.id();
        var reference = "order:" + orderId;
        var reservation = stock.reserve(
                reference,
                arrivalDate(summary),
                summary.lines().stream()
                        .map(line -> new StockReservationService.Line(line.skuId(), line.quantity()))
                        .toList());
        if (summary.couponCode() != null) {
            reserveCoupon(summary, buyer, reference);
        }
        var token = buyer == null ? accessTokens.issue(orderId) : null;
        payments.request(orderId, summary.totalCents(), correlationId);
        carts.consumePurchased(
                command.sessionId(),
                summary.lines().stream()
                        .collect(Collectors.toMap(
                                PurchaseSummaryService.Line::skuId, PurchaseSummaryService.Line::quantity)));
        idempotency.complete(subject, CheckoutIdempotency.PURCHASE, command.idempotencyKey(), orderId);
        return new AcceptedPurchase(
                orderId, "PENDING_PAYMENT", summary.totalCents(), reservation.expiresAt(), token, false);
    }

    private AcceptedPurchase replay(UUID orderId) {
        var order = orderQueries.forAdmin(orderId);
        var expiresAt = stock.find("order:" + orderId)
                .map(StockReservationService.Reservation::expiresAt)
                .orElse(null);
        return new AcceptedPurchase(orderId, order.status().name(), order.totalCents(), expiresAt, null, true);
    }

    private void reserveCoupon(PurchaseSummary summary, AccountService.Buyer buyer, String reference) {
        if (buyer == null || !buyer.emailVerified()) {
            throw new CouponRejectedException("EMAIL_NOT_VERIFIED");
        }
        var result = coupons.reserve(summary.couponCode(), buyer.email(), summary.subtotalCents(), reference);
        if (!result.reserved()) {
            throw new CouponRejectedException(result.rejection().name());
        }
        if (!result.discount().equals(summary.discount())) {
            throw new SummaryChangedException(summary);
        }
    }

    private CreateOrderCommand orderCommand(
            String subject,
            PurchaseCommand command,
            AccountService.Buyer buyer,
            String email,
            PurchaseSummary summary,
            UUID correlationId) {
        var fulfillment = summary.fulfillment();
        var destination = objectMapper.createObjectNode();
        if (fulfillment.mode() == Mode.DELIVERY) {
            var address = command.address();
            if (address == null) {
                throw new PurchaseSummaryService.InvalidSelectionException();
            }
            destination.put("recipientName", address.recipientName());
            destination.put("street", address.street());
            destination.put("number", address.number());
            destination.put("complement", address.complement());
            destination.put("district", address.district());
            destination.put("city", address.city());
            destination.put("state", address.state());
            destination.put("postalCode", fulfillment.detail());
            destination.put("service", fulfillment.label());
        } else {
            destination.put("label", fulfillment.label());
            destination.put("window", fulfillment.detail());
        }
        return new CreateOrderCommand(
                sha256(subject + "|" + command.idempotencyKey()),
                buyer == null ? null : buyer.accountId(),
                email,
                fulfillment.mode() == Mode.DELIVERY ? FulfillmentMode.DELIVERY : FulfillmentMode.PICKUP,
                summary.subtotalCents(),
                summary.shippingCents(),
                summary.discount() == null ? null : summary.discount().type().name(),
                summary.discount() == null ? null : summary.discount().value(),
                summary.discountCents(),
                summary.couponCode(),
                summary.totalCents(),
                fulfillment.preparationDays(),
                fulfillment.deliveryDays(),
                PurchaseSummaryService.PRICING_RULE_VERSION,
                destination,
                summary.lines().stream()
                        .map(line -> new CreateOrderCommand.Item(
                                line.skuId(),
                                line.productName(),
                                line.salesUnit(),
                                line.quantity(),
                                line.unitPriceCents(),
                                line.lineTotalCents()))
                        .toList(),
                correlationId);
    }

    private LocalDate arrivalDate(PurchaseSummary summary) {
        var fulfillment = summary.fulfillment();
        var days =
                fulfillment.preparationDays() + (fulfillment.deliveryDays() == null ? 0 : fulfillment.deliveryDays());
        return LocalDate.ofInstant(clock.instant(), BELEM).plusDays(days);
    }

    private static String canonicalFulfillment(PurchaseCommand command) {
        var selection = command.selection();
        if (selection.mode() == Mode.PICKUP) {
            return "PICKUP:" + selection.pickupOptionId();
        }
        var a = command.address();
        return "DELIVERY:" + selection.quoteId() + ":" + selection.inputFingerprint()
                + (a == null
                        ? ""
                        : ":"
                                + String.join(
                                        "\u001e",
                                        a.recipientName(),
                                        a.street(),
                                        a.number(),
                                        String.valueOf(a.complement()),
                                        a.district(),
                                        a.city(),
                                        a.state()));
    }

    private static String normalizedEmail(String email) {
        var normalized = AccountService.normalize(email);
        if (normalized == null || normalized.length() > 254 || !normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new InvalidContactEmailException();
        }
        return normalized;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Address(
            String recipientName,
            String street,
            String number,
            String complement,
            String district,
            String city,
            String state) {}

    /** {@code accountEmail} is the authenticated principal, if any; guests supply {@code guestEmail}. */
    public record PurchaseCommand(
            String sessionId,
            String accountEmail,
            String idempotencyKey,
            UUID snapshotId,
            long snapshotVersion,
            Selection selection,
            String guestEmail,
            Address address,
            String summaryVersion) {}

    /** {@code accessToken} is present only on the first acceptance of a guest purchase (CHK-Q02). */
    public record AcceptedPurchase(
            UUID orderId,
            String status,
            long totalCents,
            Instant reservationExpiresAt,
            String accessToken,
            boolean replayed) {}

    public static class SummaryChangedException extends RuntimeException {

        private final transient PurchaseSummary summary;

        public SummaryChangedException(PurchaseSummary summary) {
            this.summary = summary;
        }

        public PurchaseSummary summary() {
            return summary;
        }
    }

    public static class CouponRejectedException extends RuntimeException {

        public CouponRejectedException(String reason) {
            super(reason);
        }
    }

    public static class InvalidContactEmailException extends RuntimeException {}

    public static class ZeroTotalException extends RuntimeException {}
}
