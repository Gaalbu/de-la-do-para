package br.com.deladopara.payments.domain;

import static br.com.deladopara.payments.domain.PaymentStatus.AWAITING_PAYMENT;
import static br.com.deladopara.payments.domain.PaymentStatus.CONFIRMED;
import static br.com.deladopara.payments.domain.PaymentStatus.CREATING_CHECKOUT;
import static br.com.deladopara.payments.domain.PaymentStatus.DECLINED;
import static br.com.deladopara.payments.domain.PaymentStatus.REFUNDED;
import static br.com.deladopara.payments.domain.PaymentStatus.REFUND_REQUESTED;
import static br.com.deladopara.payments.domain.PaymentStatus.REQUESTED;
import static br.com.deladopara.payments.domain.PaymentStatus.UNDER_REVIEW;
import static br.com.deladopara.payments.domain.PaymentStatus.UNKNOWN;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** The SPEC-payments §7.2 transition table. Anything not listed is rejected. */
public final class PaymentTransitions {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED.put(REQUESTED, EnumSet.of(CREATING_CHECKOUT));
        ALLOWED.put(CREATING_CHECKOUT, EnumSet.of(AWAITING_PAYMENT, DECLINED, UNKNOWN));
        ALLOWED.put(UNKNOWN, EnumSet.of(AWAITING_PAYMENT, CONFIRMED, DECLINED, UNDER_REVIEW));
        ALLOWED.put(AWAITING_PAYMENT, EnumSet.of(CONFIRMED, UNDER_REVIEW));
        ALLOWED.put(CONFIRMED, EnumSet.of(REFUND_REQUESTED));
        ALLOWED.put(UNDER_REVIEW, EnumSet.of(REFUND_REQUESTED, CONFIRMED));
        ALLOWED.put(REFUND_REQUESTED, EnumSet.of(REFUNDED));
    }

    private PaymentTransitions() {}

    public static boolean allowed(PaymentStatus from, PaymentStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void validate(PaymentStatus from, PaymentStatus to) {
        if (!allowed(from, to)) {
            throw new InvalidPaymentTransitionException(from, to);
        }
    }

    public static class InvalidPaymentTransitionException extends RuntimeException {

        public InvalidPaymentTransitionException(PaymentStatus from, PaymentStatus to) {
            super("Invalid payment transition " + from + " -> " + to);
        }
    }
}
