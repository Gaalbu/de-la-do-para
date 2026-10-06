package br.com.deladopara.checkout.application;

import br.com.deladopara.inventory.application.StockReservationService;
import br.com.deladopara.orders.application.OrderService;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import br.com.deladopara.payments.adapter.persistence.PaymentRepository;
import br.com.deladopara.payments.application.PaymentIntentService;
import br.com.deladopara.payments.domain.PaymentStatus;
import br.com.deladopara.pricing.application.CouponReservationService;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels a purchase as one local transaction (SPEC-checkout §A6, C68). The order is locked first, then the
 * reservation, coupon and payment, like every coordinated transition, so a cancellation and a payment outcome, a
 * pickup confirmation or a dispatch for the same order are decided one after the other on the current state.
 *
 * <ul>
 *   <li>Unpaid: stock and coupon released, order CANCELLED. A payment confirmed later is refunded by the outcome
 *       handler.
 *   <li>Paid and not with the carrier nor picked up (D12, D29, D31): committed stock returned to its lots (CHK-Q03
 *       proposal), coupon stays CONSUMED until the refund settles (D34), order CANCELLED, full refund requested.
 *   <li>In transit (D30): order UNDER_REVIEW for an administrator, no automatic refund.
 * </ul>
 */
@Service
public class CheckoutCancellationService {

    public static final String CANCELLED = "ORDER_CANCELLED";
    public static final String REVIEW = "CANCELLATION_REQUESTED";

    private static final Set<OrderStatus> PAID_NOT_DISPATCHED =
            EnumSet.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP);

    private final OrderService orders;
    private final StockReservationService stock;
    private final CouponReservationService coupons;
    private final PaymentIntentService payments;
    private final PaymentRepository paymentRepository;

    public CheckoutCancellationService(
            OrderService orders,
            StockReservationService stock,
            CouponReservationService coupons,
            PaymentIntentService payments,
            PaymentRepository paymentRepository) {
        this.orders = orders;
        this.stock = stock;
        this.coupons = coupons;
        this.payments = payments;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Returns the order's status after the request. Asking again for an order already cancelled or under review
     * changes nothing, so a repeated request keeps the first outcome; any other state is refused with
     * {@link OrderNotCancellableException}.
     */
    @Transactional
    public OrderStatus cancel(UUID orderId, OrderActor actor, UUID correlationId) {
        var order = orders.lock(orderId);
        var reference = "order:" + orderId;
        if (order.status() == OrderStatus.CANCELLED || order.status() == OrderStatus.UNDER_REVIEW) {
            return order.status();
        }
        if (order.status() == OrderStatus.PENDING_PAYMENT) {
            var intent = paymentRepository.findByOrder(orderId).orElse(null);
            if (intent != null && intent.status() == PaymentStatus.CONFIRMED) {
                // The confirmation is about to be applied; the order is no longer an unpaid one.
                throw new OrderNotCancellableException(order.status());
            }
            stock.release(reference);
            if (order.couponCode() != null) {
                coupons.release(reference);
            }
            return orders.transition(orderId, OrderStatus.CANCELLED, actor, CANCELLED, correlationId);
        }
        if (PAID_NOT_DISPATCHED.contains(order.status())) {
            stock.returnCommitted(reference, "pedido cancelado");
            orders.transition(orderId, OrderStatus.CANCELLED, actor, CANCELLED, correlationId);
            var intent = paymentRepository.findByOrder(orderId).orElseThrow();
            payments.transition(intent.id(), PaymentStatus.REFUND_REQUESTED, CANCELLED, correlationId);
            return OrderStatus.CANCELLED;
        }
        if (order.status() == OrderStatus.IN_TRANSIT) {
            return orders.transition(orderId, OrderStatus.UNDER_REVIEW, actor, REVIEW, correlationId);
        }
        throw new OrderNotCancellableException(order.status());
    }

    public static class OrderNotCancellableException extends RuntimeException {

        private final OrderStatus status;

        public OrderNotCancellableException(OrderStatus status) {
            super("Order in status " + status + " cannot be cancelled");
            this.status = status;
        }

        public OrderStatus status() {
            return status;
        }
    }
}
