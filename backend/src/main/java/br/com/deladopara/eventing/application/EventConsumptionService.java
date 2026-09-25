package br.com.deladopara.eventing.application;

import br.com.deladopara.eventing.adapter.persistence.EventConsumptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventConsumptionService {

    private final EventConsumptionRepository consumptions;
    private final List<EventHandler> handlers;
    private final Clock clock;

    public EventConsumptionService(EventConsumptionRepository consumptions, List<EventHandler> handlers, Clock clock) {
        this.consumptions = consumptions;
        this.handlers = List.copyOf(handlers);
        this.clock = clock;
    }

    @Transactional
    public EventConsumptionOutcome consume(EventEnvelope event) {
        var handler = handlers.stream()
                .filter(candidate -> candidate.eventType().equals(event.eventType()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedEventException(event.eventType(), event.schemaVersion()));
        if (handler.schemaVersion() != event.schemaVersion()) {
            throw new UnsupportedEventException(event.eventType(), event.schemaVersion());
        }
        var receivedAt = clock.instant();
        var cursor = consumptions.lockCursor(handler.handlerName(), event.aggregateId());
        if (event.aggregateVersion() <= cursor) {
            return EventConsumptionOutcome.DUPLICATE;
        }
        if (!consumptions.insertPending(handler.handlerName(), event, receivedAt)) {
            return EventConsumptionOutcome.DUPLICATE;
        }
        if (event.aggregateVersion() > cursor + 1) {
            return EventConsumptionOutcome.PENDING_ORDER;
        }

        apply(handler, event, receivedAt);
        drain(handler, event.aggregateId(), event.aggregateVersion() + 1);
        return EventConsumptionOutcome.APPLIED;
    }

    private void drain(EventHandler handler, String aggregateId, long nextVersion) {
        var pending = consumptions.findPendingVersion(handler.handlerName(), aggregateId, nextVersion);
        if (pending.isEmpty()) {
            return;
        }
        var event = pending.orElseThrow().envelope();
        apply(handler, event, clock.instant());
        drain(handler, aggregateId, nextVersion + 1);
    }

    private void apply(EventHandler handler, EventEnvelope event, Instant appliedAt) {
        handler.handle(event);
        consumptions.markApplied(handler.handlerName(), event.eventId(), appliedAt);
        consumptions.advanceCursor(
                handler.handlerName(),
                event.aggregateId(),
                event.aggregateVersion() - 1,
                event.aggregateVersion(),
                appliedAt);
    }
}
