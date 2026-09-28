package br.com.deladopara.checkout.application;

import br.com.deladopara.eventing.application.EventEnvelope;
import br.com.deladopara.eventing.application.EventHandler;
import br.com.deladopara.inventory.application.StockReservationService;
import br.com.deladopara.orders.application.OrderService;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import br.com.deladopara.payments.adapter.persistence.PaymentRepository;
import br.com.deladopara.payments.application.PaymentIntentService;
import br.com.deladopara.payments.domain.PaymentStatus;
import br.com.deladopara.pricing.application.CouponReservationService;
import java.time.Clock;
import java.util.UUID;

/**
 * Applies payment facts to the purchase (SPEC-checkout §A6) inside the consumption transaction, so the offset is
 * committed only after the order, stock and coupon changes. One instance is registered per {@code payment.*} type,
 * all under the same handler name: the consumer's per-aggregate cursor needs every version of the payment.
 */
public class PaymentOutcomeHandler implements EventHandler {

    public static final String NAME = "checkout.payment-outcome";

    private final String eventType;
    private final OrderService orders;
    private final StockReservationService stock;
    private final CouponReservationService coupons;
    private final PaymentIntentService payments;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public PaymentOutcomeHandler(
            String eventType,
            OrderService orders,
            StockReservationService stock,
            CouponReservationService coupons,
            PaymentIntentService payments,
            PaymentRepository paymentRepository,
            Clock clock) {
        this.eventType = eventType;
        this.orders = orders;
        this.stock = stock;
        this.coupons = coupons;
        this.payments = payments;
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    @Override
    public String handlerName() {
        return NAME;
    }

    @Override
    public String eventType() {
        return eventType;
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public void handle(EventEnvelope event) {
        var payload = event.payload();
        if (!"payment.status_changed".equals(event.eventType())
                || !PaymentStatus.CONFIRMED.name().equals(payload.path("to").asText())) {
            return;
        }
        var orderId = UUID.fromString(payload.path("orderId").asText());
        var intentId = UUID.fromString(payload.path("paymentIntentId").asText());
        var order = orders.lock(orderId);
        var intent =
                paymentRepository.lock(intentId).orElseThrow(PaymentIntentService.PaymentIntentNotFoundException::new);
        if (!intent.orderId().equals(orderId)) {
            return;
        }
        if (intent.amountCents() != order.totalCents()) {
            return;
        }
        var reference = "order:" + orderId;
        var reservation = stock.find(reference).orElse(null);
        var onTime = order.status() == OrderStatus.PENDING_PAYMENT
                && reservation != null
                && reservation.status() == StockReservationService.Status.ACTIVE
                && clock.instant().isBefore(reservation.expiresAt());
        if (onTime) {
            stock.commit(reference);
            if (order.couponCode() != null) {
                coupons.consume(reference);
            }
            orders.transition(orderId, OrderStatus.PAID, OrderActor.SYSTEM, null, event.correlationId());
            return;
        }
        if (order.status() == OrderStatus.PENDING_PAYMENT) {
            orders.transition(
                    orderId, OrderStatus.UNDER_REVIEW, OrderActor.SYSTEM, "LATE_PAYMENT", event.correlationId());
            stock.release(reference);
            if (order.couponCode() != null) {
                coupons.release(reference);
            }
        }
        if (order.status() == OrderStatus.PENDING_PAYMENT || order.status() == OrderStatus.EXPIRED) {
            payments.transition(intentId, PaymentStatus.REFUND_REQUESTED, "LATE_PAYMENT", event.correlationId());
        }
    }
}
