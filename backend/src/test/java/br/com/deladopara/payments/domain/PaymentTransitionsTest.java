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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.payments.domain.PaymentTransitions.InvalidPaymentTransitionException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PaymentTransitionsTest {

    private record Pair(PaymentStatus from, PaymentStatus to) {}

    private static final Set<Pair> SPEC = Set.of(
            new Pair(REQUESTED, CREATING_CHECKOUT),
            new Pair(CREATING_CHECKOUT, AWAITING_PAYMENT),
            new Pair(CREATING_CHECKOUT, DECLINED),
            new Pair(CREATING_CHECKOUT, UNKNOWN),
            new Pair(UNKNOWN, AWAITING_PAYMENT),
            new Pair(UNKNOWN, CONFIRMED),
            new Pair(UNKNOWN, DECLINED),
            new Pair(UNKNOWN, UNDER_REVIEW),
            new Pair(AWAITING_PAYMENT, CONFIRMED),
            new Pair(AWAITING_PAYMENT, UNDER_REVIEW),
            new Pair(CONFIRMED, REFUND_REQUESTED),
            new Pair(UNDER_REVIEW, REFUND_REQUESTED),
            new Pair(UNDER_REVIEW, CONFIRMED),
            new Pair(REFUND_REQUESTED, REFUNDED));

    @Test
    void matchesTheSpecTableExactly() {
        var allowed = new HashSet<Pair>();
        for (var from : PaymentStatus.values()) {
            for (var to : PaymentStatus.values()) {
                if (PaymentTransitions.allowed(from, to)) {
                    allowed.add(new Pair(from, to));
                }
            }
        }
        assertThat(allowed).isEqualTo(SPEC);
    }

    @Test
    void terminalStatesNeverLeave() {
        for (var from : List.of(DECLINED, REFUNDED)) {
            assertThat(from.terminal()).isTrue();
            for (var to : PaymentStatus.values()) {
                assertThat(PaymentTransitions.allowed(from, to)).isFalse();
            }
        }
    }

    @Test
    void confirmationNeverSkipsAPendingCheckoutAndRefundNeedsPayment() {
        assertThatThrownBy(() -> PaymentTransitions.validate(REQUESTED, CONFIRMED))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThatThrownBy(() -> PaymentTransitions.validate(AWAITING_PAYMENT, REFUND_REQUESTED))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThatThrownBy(() -> PaymentTransitions.validate(CONFIRMED, AWAITING_PAYMENT))
                .isInstanceOf(InvalidPaymentTransitionException.class);
    }
}
