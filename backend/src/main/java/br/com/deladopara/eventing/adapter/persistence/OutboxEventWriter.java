package br.com.deladopara.eventing.adapter.persistence;

import br.com.deladopara.eventing.domain.OutboxEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxEventWriter {

    private final OutboxEventRepository events;

    public OutboxEventWriter(OutboxEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OutboxEvent event) {
        events.save(new OutboxEventEntity(event));
    }
}
