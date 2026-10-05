package br.com.deladopara.orders.application;

import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import java.util.Map;
import java.util.UUID;

public interface OrderFulfillmentPort {

    LockedOrder lock(UUID orderId);

    OrderStatus transition(UUID orderId, OrderStatus to, OrderActor actor, String reason, UUID correlationId);

    record LockedOrder(
            UUID id,
            FulfillmentMode mode,
            OrderStatus status,
            int sequence,
            long totalCents,
            String couponCode,
            Map<String, Object> destination) {}
}
