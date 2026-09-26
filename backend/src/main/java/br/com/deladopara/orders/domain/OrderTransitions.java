package br.com.deladopara.orders.domain;

import static br.com.deladopara.orders.domain.OrderActor.ADMIN;
import static br.com.deladopara.orders.domain.OrderActor.CUSTOMER;
import static br.com.deladopara.orders.domain.OrderActor.SYSTEM;
import static br.com.deladopara.orders.domain.OrderStatus.CANCELLED;
import static br.com.deladopara.orders.domain.OrderStatus.DELIVERED;
import static br.com.deladopara.orders.domain.OrderStatus.EXPIRED;
import static br.com.deladopara.orders.domain.OrderStatus.IN_TRANSIT;
import static br.com.deladopara.orders.domain.OrderStatus.PAID;
import static br.com.deladopara.orders.domain.OrderStatus.PENDING_PAYMENT;
import static br.com.deladopara.orders.domain.OrderStatus.PICKED_UP;
import static br.com.deladopara.orders.domain.OrderStatus.PREPARING;
import static br.com.deladopara.orders.domain.OrderStatus.READY_FOR_PICKUP;
import static br.com.deladopara.orders.domain.OrderStatus.UNDER_REVIEW;

import java.util.List;
import java.util.Set;

/** The SPEC-orders §7.2 transition table. Anything not listed is rejected. */
public final class OrderTransitions {

    private record Rule(
            OrderStatus from, OrderStatus to, Set<OrderActor> actors, FulfillmentMode mode, boolean reasonRequired) {}

    private static final List<Rule> RULES = List.of(
            rule(PENDING_PAYMENT, PAID, Set.of(SYSTEM), null, false),
            rule(PENDING_PAYMENT, EXPIRED, Set.of(SYSTEM), null, false),
            rule(PENDING_PAYMENT, UNDER_REVIEW, Set.of(SYSTEM), null, true),
            rule(PENDING_PAYMENT, CANCELLED, Set.of(CUSTOMER, ADMIN), null, true),
            rule(PAID, PREPARING, Set.of(ADMIN, SYSTEM), null, false),
            rule(PAID, CANCELLED, Set.of(CUSTOMER, ADMIN, SYSTEM), null, true),
            rule(PREPARING, CANCELLED, Set.of(CUSTOMER, ADMIN, SYSTEM), null, true),
            rule(PREPARING, READY_FOR_PICKUP, Set.of(ADMIN), FulfillmentMode.PICKUP, false),
            rule(PREPARING, IN_TRANSIT, Set.of(SYSTEM), FulfillmentMode.DELIVERY, false),
            rule(IN_TRANSIT, DELIVERED, Set.of(SYSTEM), FulfillmentMode.DELIVERY, false),
            rule(READY_FOR_PICKUP, PICKED_UP, Set.of(ADMIN), FulfillmentMode.PICKUP, false),
            rule(READY_FOR_PICKUP, CANCELLED, Set.of(CUSTOMER, ADMIN), FulfillmentMode.PICKUP, true),
            rule(READY_FOR_PICKUP, UNDER_REVIEW, Set.of(SYSTEM), FulfillmentMode.PICKUP, true),
            rule(IN_TRANSIT, UNDER_REVIEW, Set.of(CUSTOMER, ADMIN), FulfillmentMode.DELIVERY, true),
            rule(UNDER_REVIEW, PAID, Set.of(ADMIN), null, true),
            rule(UNDER_REVIEW, READY_FOR_PICKUP, Set.of(ADMIN), FulfillmentMode.PICKUP, true),
            rule(UNDER_REVIEW, IN_TRANSIT, Set.of(ADMIN), FulfillmentMode.DELIVERY, true),
            rule(UNDER_REVIEW, CANCELLED, Set.of(ADMIN), null, true));

    private OrderTransitions() {}

    private static Rule rule(
            OrderStatus from, OrderStatus to, Set<OrderActor> actors, FulfillmentMode mode, boolean reasonRequired) {
        return new Rule(from, to, actors, mode, reasonRequired);
    }

    /** Validates a transition; throws {@link InvalidOrderTransitionException} when it is not allowed. */
    public static void validate(
            OrderStatus from, OrderStatus to, OrderActor actor, FulfillmentMode mode, String reason) {
        var rule = RULES.stream()
                .filter(candidate -> candidate.from() == from && candidate.to() == to)
                .findFirst()
                .orElseThrow(() -> new InvalidOrderTransitionException(from, to, "transition not allowed"));
        if (!rule.actors().contains(actor)) {
            throw new InvalidOrderTransitionException(from, to, "actor " + actor + " not allowed");
        }
        if (rule.mode() != null && rule.mode() != mode) {
            throw new InvalidOrderTransitionException(from, to, "not valid for " + mode);
        }
        if (rule.reasonRequired() && (reason == null || reason.isBlank())) {
            throw new InvalidOrderTransitionException(from, to, "reason required");
        }
    }

    public static class InvalidOrderTransitionException extends RuntimeException {

        public InvalidOrderTransitionException(OrderStatus from, OrderStatus to, String detail) {
            super("Invalid order transition " + from + " -> " + to + ": " + detail);
        }
    }
}
