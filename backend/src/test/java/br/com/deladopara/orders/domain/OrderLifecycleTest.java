package br.com.deladopara.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.orders.domain.OrderTransitions.InvalidOrderTransitionException;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class OrderLifecycleTest {

    private static boolean allowed(OrderStatus from, OrderStatus to, OrderActor actor, FulfillmentMode mode) {
        try {
            OrderTransitions.validate(from, to, actor, mode, "motivo");
            return true;
        } catch (InvalidOrderTransitionException rejected) {
            return false;
        }
    }

    @Test
    void terminalStatesNeverLeave() {
        for (var from : OrderStatus.values()) {
            if (!from.terminal()) {
                continue;
            }
            for (var to : OrderStatus.values()) {
                for (var actor : OrderActor.values()) {
                    for (var mode : FulfillmentMode.values()) {
                        assertThat(allowed(from, to, actor, mode))
                                .as("%s -> %s by %s (%s)", from, to, actor, mode)
                                .isFalse();
                    }
                }
            }
        }
    }

    @Test
    void exactlyTheDocumentedPairsAreReachableByAtLeastOneActorAndMode() {
        var reachable = new ArrayList<String>();
        for (var from : OrderStatus.values()) {
            for (var to : OrderStatus.values()) {
                var any = false;
                for (var actor : OrderActor.values()) {
                    for (var mode : FulfillmentMode.values()) {
                        any |= allowed(from, to, actor, mode);
                    }
                }
                if (any) {
                    reachable.add(from + ">" + to);
                }
            }
        }

        assertThat(reachable)
                .containsExactlyInAnyOrder(
                        "PENDING_PAYMENT>PAID",
                        "PENDING_PAYMENT>EXPIRED",
                        "PENDING_PAYMENT>UNDER_REVIEW",
                        "PENDING_PAYMENT>CANCELLED",
                        "PAID>PREPARING",
                        "PAID>CANCELLED",
                        "PREPARING>CANCELLED",
                        "PREPARING>READY_FOR_PICKUP",
                        "PREPARING>IN_TRANSIT",
                        "IN_TRANSIT>DELIVERED",
                        "READY_FOR_PICKUP>PICKED_UP",
                        "READY_FOR_PICKUP>CANCELLED",
                        "READY_FOR_PICKUP>UNDER_REVIEW",
                        "IN_TRANSIT>UNDER_REVIEW",
                        "UNDER_REVIEW>PAID",
                        "UNDER_REVIEW>READY_FOR_PICKUP",
                        "UNDER_REVIEW>IN_TRANSIT",
                        "UNDER_REVIEW>CANCELLED");
    }

    @Test
    void modeRestrictsPickupAndDeliverySteps() {
        assertThat(allowed(
                        OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderActor.ADMIN, FulfillmentMode.PICKUP))
                .isTrue();
        assertThat(allowed(
                        OrderStatus.PREPARING,
                        OrderStatus.READY_FOR_PICKUP,
                        OrderActor.ADMIN,
                        FulfillmentMode.DELIVERY))
                .isFalse();
        assertThat(allowed(OrderStatus.PREPARING, OrderStatus.IN_TRANSIT, OrderActor.SYSTEM, FulfillmentMode.DELIVERY))
                .isTrue();
        assertThat(allowed(OrderStatus.PREPARING, OrderStatus.IN_TRANSIT, OrderActor.SYSTEM, FulfillmentMode.PICKUP))
                .isFalse();
    }

    @Test
    void actorsAreRestrictedPerTransition() {
        assertThat(allowed(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderActor.SYSTEM, FulfillmentMode.DELIVERY))
                .isTrue();
        assertThat(allowed(
                        OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderActor.CUSTOMER, FulfillmentMode.DELIVERY))
                .isFalse();
        assertThat(allowed(
                        OrderStatus.READY_FOR_PICKUP,
                        OrderStatus.PICKED_UP,
                        OrderActor.CUSTOMER,
                        FulfillmentMode.PICKUP))
                .isFalse();
    }

    @Test
    void cancellationAfterCarrierHandoffGoesToReviewNotCancelled() {
        assertThat(allowed(OrderStatus.IN_TRANSIT, OrderStatus.CANCELLED, OrderActor.ADMIN, FulfillmentMode.DELIVERY))
                .isFalse();
        assertThat(allowed(
                        OrderStatus.IN_TRANSIT,
                        OrderStatus.UNDER_REVIEW,
                        OrderActor.CUSTOMER,
                        FulfillmentMode.DELIVERY))
                .isTrue();
    }

    @Test
    void reasonIsRequiredWhereDocumented() {
        assertThatThrownBy(() -> OrderTransitions.validate(
                        OrderStatus.PAID, OrderStatus.CANCELLED, OrderActor.CUSTOMER, FulfillmentMode.DELIVERY, " "))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatCode(() -> OrderTransitions.validate(
                        OrderStatus.PAID, OrderStatus.PREPARING, OrderActor.ADMIN, FulfillmentMode.DELIVERY, null))
                .doesNotThrowAnyException();
    }
}
