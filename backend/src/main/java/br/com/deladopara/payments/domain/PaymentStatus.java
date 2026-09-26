package br.com.deladopara.payments.domain;

public enum PaymentStatus {
    REQUESTED,
    CREATING_CHECKOUT,
    AWAITING_PAYMENT,
    CONFIRMED,
    DECLINED,
    UNKNOWN,
    UNDER_REVIEW,
    REFUND_REQUESTED,
    REFUNDED;

    public boolean terminal() {
        return this == DECLINED || this == REFUNDED;
    }
}
