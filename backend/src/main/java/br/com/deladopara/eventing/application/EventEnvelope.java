package br.com.deladopara.eventing.application;

import br.com.deladopara.eventing.domain.OutboxEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String aggregateId,
        long aggregateVersion,
        String occurredAt,
        UUID correlationId,
        UUID causationId,
        JsonNode payload) {

    public static EventEnvelope from(OutboxEvent event) {
        return new EventEnvelope(
                event.eventId(),
                event.eventType(),
                event.schemaVersion(),
                event.aggregateId(),
                event.aggregateVersion(),
                event.occurredAt().toString(),
                event.correlationId(),
                event.causationId(),
                event.payload());
    }
}
