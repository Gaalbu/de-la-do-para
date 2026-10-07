package br.com.deladopara.eventing.application;

import br.com.deladopara.eventing.domain.OutboxEvent;
import java.time.Instant;

/** {@code traceParent} is the W3C trace context stored with the event, or null. */
public record ClaimedOutboxEvent(OutboxEvent event, Instant leaseUntil, String traceParent) {

    public ClaimedOutboxEvent(OutboxEvent event, Instant leaseUntil) {
        this(event, leaseUntil, null);
    }
}
