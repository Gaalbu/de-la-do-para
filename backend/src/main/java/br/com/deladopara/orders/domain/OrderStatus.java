package br.com.deladopara.orders.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PREPARING,
    READY_FOR_PICKUP,
    IN_TRANSIT,
    DELIVERED,
    PICKED_UP,
    CANCELLED,
    EXPIRED,
    UNDER_REVIEW;

    public boolean terminal() {
        return this == DELIVERED || this == PICKED_UP || this == CANCELLED || this == EXPIRED;
    }
}
