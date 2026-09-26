package br.com.deladopara.orders.application;

import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read model of one order: immutable snapshot plus status history. */
public record OrderView(
        UUID id,
        OrderStatus status,
        FulfillmentMode mode,
        String contactEmail,
        String currency,
        long subtotalCents,
        long shippingCents,
        long discountCents,
        String couponCode,
        long totalCents,
        int preparationDays,
        Integer deliveryDays,
        Map<String, Object> destination,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items,
        List<Transition> history) {

    public record Item(String productName, String skuLabel, int quantity, long unitPriceCents, long lineTotalCents) {}

    public record Transition(
            int sequence, OrderStatus from, OrderStatus to, OrderActor actor, String reason, Instant occurredAt) {}

    public record Summary(
            UUID id, OrderStatus status, FulfillmentMode mode, long totalCents, int itemCount, Instant createdAt) {}
}
