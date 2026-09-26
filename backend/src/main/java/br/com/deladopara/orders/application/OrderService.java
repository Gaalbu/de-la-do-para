package br.com.deladopara.orders.application;

import br.com.deladopara.eventing.adapter.persistence.OutboxEventWriter;
import br.com.deladopara.eventing.domain.OutboxEvent;
import br.com.deladopara.orders.adapter.persistence.OrderRepository;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import br.com.deladopara.orders.domain.OrderTransitions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates orders and applies status transitions. Row, history and outbox event change in one transaction, so a
 * failed transition leaves all three untouched.
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final OutboxEventWriter outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OrderService(OrderRepository orders, OutboxEventWriter outbox, ObjectMapper objectMapper, Clock clock) {
        this.orders = orders;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Idempotent by checkout key: a replay returns the existing order and writes nothing. */
    @Transactional
    public CreatedOrder create(CreateOrderCommand command) {
        var existing = orders.findByCheckoutKey(command.checkoutKey());
        if (existing.isPresent()) {
            return new CreatedOrder(existing.get().id(), false);
        }
        var id = UUID.randomUUID();
        var now = clock.instant();
        if (!orders.insert(id, command, command.destination().toString(), now)) {
            return new CreatedOrder(
                    orders.findByCheckoutKey(command.checkoutKey())
                            .orElseThrow()
                            .id(),
                    false);
        }
        orders.appendHistory(
                id, 0, null, OrderStatus.PENDING_PAYMENT, OrderActor.SYSTEM, null, now, command.correlationId());
        var payload = objectMapper.createObjectNode();
        payload.put("orderId", id.toString());
        payload.put("totalCents", command.totalCents());
        payload.put("currency", "BRL");
        payload.put("mode", command.mode().name());
        payload.put("itemCount", command.items().size());
        outbox.append(new OutboxEvent(
                UUID.randomUUID(),
                "order.created",
                1,
                id.toString(),
                0,
                now,
                command.correlationId(),
                command.correlationId(),
                payload));
        return new CreatedOrder(id, true);
    }

    @Transactional
    public OrderStatus transition(UUID orderId, OrderStatus to, OrderActor actor, String reason, UUID correlationId) {
        var order = orders.lock(orderId).orElseThrow(OrderNotFoundException::new);
        if (order.status() == to) {
            return to;
        }
        OrderTransitions.validate(order.status(), to, actor, order.mode(), reason);
        var now = clock.instant();
        var sequence = order.sequence() + 1;
        orders.appendHistory(orderId, sequence, order.status(), to, actor, reason, now, correlationId);
        orders.updateStatus(orderId, to, sequence, now);
        var payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("from", order.status().name());
        payload.put("to", to.name());
        if (reason != null) {
            payload.put("reason", reason);
        }
        payload.put("sequence", sequence);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(),
                "order.status_changed",
                1,
                orderId.toString(),
                sequence,
                now,
                correlationId,
                correlationId,
                payload));
        return to;
    }

    public record CreatedOrder(UUID id, boolean created) {}

    public static class OrderNotFoundException extends RuntimeException {}
}
