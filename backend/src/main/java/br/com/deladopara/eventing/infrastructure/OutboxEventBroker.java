package br.com.deladopara.eventing.infrastructure;

import br.com.deladopara.eventing.domain.OutboxEvent;

public interface OutboxEventBroker {

    void publish(OutboxEvent event) throws OutboxPublishException;

    /** Publishes with the stored W3C trace context; brokers without headers ignore it. */
    default void publish(OutboxEvent event, String traceParent) throws OutboxPublishException {
        publish(event);
    }
}
