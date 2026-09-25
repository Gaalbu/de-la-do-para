package br.com.deladopara.payments.application;

import br.com.deladopara.eventing.adapter.persistence.OutboxEventWriter;
import br.com.deladopara.eventing.domain.OutboxEvent;
import br.com.deladopara.payments.adapter.persistence.PaymentRepository;
import br.com.deladopara.payments.domain.OperationKind;
import br.com.deladopara.payments.domain.PaymentStatus;
import br.com.deladopara.payments.domain.PaymentTransitions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the intent to charge an order before any provider call. The intent, its CREATE_CHECKOUT operation and the
 * {@code payment.checkout_requested} event are written in the caller's transaction.
 */
@Service
public class PaymentIntentService {

    private final PaymentRepository payments;
    private final OutboxEventWriter outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaymentIntentService(
            PaymentRepository payments, OutboxEventWriter outbox, ObjectMapper objectMapper, Clock clock) {
        this.payments = payments;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Idempotent by order: a replay returns the existing intent and writes nothing. */
    @Transactional
    public UUID request(UUID orderId, long amountCents, UUID correlationId) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("A payment intent needs a positive amount");
        }
        var existing = payments.findByOrder(orderId);
        if (existing.isPresent()) {
            return existing.get().id();
        }
        var id = UUID.randomUUID();
        var now = clock.instant();
        if (!payments.insertIntent(id, orderId, amountCents, now)) {
            return payments.findByOrder(orderId).orElseThrow().id();
        }
        payments.insertOperation(UUID.randomUUID(), id, OperationKind.CREATE_CHECKOUT, now);
        var payload = payload(id, orderId);
        payload.put("amountCents", amountCents);
        payload.put("currency", "BRL");
        emit("payment.checkout_requested", id, 0, correlationId, payload);
        return id;
    }

    /** Applies one §7.2 transition with a {@code payment.status_changed} event; same-state replays are no-ops. */
    @Transactional
    public PaymentStatus transition(UUID intentId, PaymentStatus to, String reason, UUID correlationId) {
        var intent = payments.lock(intentId).orElseThrow(PaymentIntentNotFoundException::new);
        if (intent.status() == to) {
            return to;
        }
        PaymentTransitions.validate(intent.status(), to);
        var version = intent.version() + 1;
        payments.updateStatus(intentId, to, version, reason, clock.instant());
        var payload = payload(intentId, intent.orderId());
        payload.put("from", intent.status().name());
        payload.put("to", to.name());
        payload.put("reason", reason);
        emit("payment.status_changed", intentId, version, correlationId, payload);
        return to;
    }

    private ObjectNode payload(UUID intentId, UUID orderId) {
        var payload = objectMapper.createObjectNode();
        payload.put("paymentIntentId", intentId.toString());
        payload.put("orderId", orderId.toString());
        return payload;
    }

    private void emit(String type, UUID intentId, long version, UUID correlationId, ObjectNode payload) {
        outbox.append(new OutboxEvent(
                UUID.randomUUID(),
                type,
                1,
                intentId.toString(),
                version,
                clock.instant(),
                correlationId,
                correlationId,
                payload));
    }

    public static class PaymentIntentNotFoundException extends RuntimeException {}
}
