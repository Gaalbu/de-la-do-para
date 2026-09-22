package br.com.deladopara.eventing.infrastructure;

import br.com.deladopara.eventing.domain.OutboxEvent;

public interface OutboxEventBroker {

    void publish(OutboxEvent event) throws OutboxPublishException;
}
