package br.com.deladopara.checkout.application;

import br.com.deladopara.inventory.application.StockReservationService;
import br.com.deladopara.orders.application.OrderService;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import br.com.deladopara.payments.adapter.persistence.PaymentRepository;
import br.com.deladopara.payments.domain.PaymentStatus;
import br.com.deladopara.pricing.application.CouponReservationService;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expires unpaid purchases whose 15-minute stock hold ran out (SPEC-checkout §A6, C66). Each order is decided under
 * its own lock, taken before the reservation, coupon and payment as every coordinated transition does, so a payment
 * outcome for the same order either runs first or sees the expiry. A payment already CONFIRMED is left to the outcome
 * handler, which records it as late and requests the refund (D13, V10); an EXPIRED order stays terminal.
 */
@Service
public class CheckoutExpirationService {

    public static final String REASON = "RESERVATION_EXPIRED";

    private final JdbcTemplate jdbc;
    private final OrderService orders;
    private final StockReservationService stock;
    private final CouponReservationService coupons;
    private final PaymentRepository payments;
    private final Clock clock;

    public CheckoutExpirationService(
            JdbcTemplate jdbc,
            OrderService orders,
            StockReservationService stock,
            CouponReservationService coupons,
            PaymentRepository payments,
            Clock clock) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.stock = stock;
        this.coupons = coupons;
        this.payments = payments;
        this.clock = clock;
    }

    /** Pending orders whose hold is due, oldest first; read without locks, each one is re-checked by expire. */
    public List<UUID> dueOrders(int limit) {
        return jdbc.query("""
                SELECT o.id FROM purchase_order o
                JOIN inventory_reservation r ON r.reference = 'order:' || o.id
                WHERE o.status = 'PENDING_PAYMENT' AND r.status = 'ACTIVE' AND r.expires_at <= ?
                ORDER BY r.expires_at, o.id
                LIMIT ?
                """, (rs, row) -> rs.getObject("id", UUID.class), Timestamp.from(clock.instant()), limit);
    }

    /** Returns true only for the call that expired the order; repeats and lost races return false. */
    @Transactional
    public boolean expire(UUID orderId) {
        var order = orders.lock(orderId);
        if (order.status() != OrderStatus.PENDING_PAYMENT) {
            return false;
        }
        var reference = "order:" + orderId;
        var reservation = stock.find(reference).orElse(null);
        if (reservation == null
                || reservation.status() != StockReservationService.Status.ACTIVE
                || clock.instant().isBefore(reservation.expiresAt())) {
            return false;
        }
        var confirmed = payments.findByOrder(orderId)
                .map(intent -> intent.status() == PaymentStatus.CONFIRMED)
                .orElse(false);
        if (confirmed) {
            return false;
        }
        stock.release(reference);
        if (order.couponCode() != null) {
            coupons.release(reference);
        }
        orders.transition(orderId, OrderStatus.EXPIRED, OrderActor.SYSTEM, REASON, UUID.randomUUID());
        return true;
    }
}
