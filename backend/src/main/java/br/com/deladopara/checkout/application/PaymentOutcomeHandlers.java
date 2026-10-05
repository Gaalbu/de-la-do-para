package br.com.deladopara.checkout.application;

import br.com.deladopara.inventory.application.StockReservationService;
import br.com.deladopara.orders.application.OrderService;
import br.com.deladopara.payments.adapter.persistence.PaymentRepository;
import br.com.deladopara.payments.application.PaymentIntentService;
import br.com.deladopara.pricing.application.CouponReservationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PaymentOutcomeHandlers {

    private static PaymentOutcomeHandler handler(
            String type,
            OrderService orders,
            StockReservationService stock,
            CouponReservationService coupons,
            PaymentIntentService payments,
            PaymentRepository paymentRepository,
            Clock clock) {
        return new PaymentOutcomeHandler(type, orders, stock, coupons, payments, paymentRepository, clock);
    }

    @Bean
    PaymentOutcomeHandler paymentCheckoutRequestedHandler(
            OrderService o,
            StockReservationService s,
            CouponReservationService c,
            PaymentIntentService p,
            PaymentRepository r,
            Clock k) {
        return handler("payment.checkout_requested", o, s, c, p, r, k);
    }

    @Bean
    PaymentOutcomeHandler paymentCheckoutAvailableHandler(
            OrderService o,
            StockReservationService s,
            CouponReservationService c,
            PaymentIntentService p,
            PaymentRepository r,
            Clock k) {
        return handler("payment.checkout_available", o, s, c, p, r, k);
    }

    @Bean
    PaymentOutcomeHandler paymentStatusChangedHandler(
            OrderService o,
            StockReservationService s,
            CouponReservationService c,
            PaymentIntentService p,
            PaymentRepository r,
            Clock k) {
        return handler("payment.status_changed", o, s, c, p, r, k);
    }

    @Bean
    PaymentOutcomeHandler paymentRefundRequestedHandler(
            OrderService o,
            StockReservationService s,
            CouponReservationService c,
            PaymentIntentService p,
            PaymentRepository r,
            Clock k) {
        return handler("payment.refund_requested", o, s, c, p, r, k);
    }

    @Bean
    PaymentOutcomeHandler paymentRefundedHandler(
            OrderService o,
            StockReservationService s,
            CouponReservationService c,
            PaymentIntentService p,
            PaymentRepository r,
            Clock k) {
        return handler("payment.refunded", o, s, c, p, r, k);
    }
}
