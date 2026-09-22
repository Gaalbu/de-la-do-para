package br.com.deladopara.eventing.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        JsonNode payload,
        OutboxEventStatus status,
        Instant availableAt,
        int attemptCount,
        Instant leaseUntil,
        Instant createdAt) {

    public OutboxEvent(
            UUID eventId,
            String eventType,
            int schemaVersion,
            String aggregateId,
            long aggregateVersion,
            Instant occurredAt,
            UUID correlationId,
            UUID causationId,
            JsonNode payload) {
        this(
                eventId,
                eventType,
                schemaVersion,
                aggregateId,
                aggregateVersion,
                occurredAt,
                correlationId,
                causationId,
                payload,
                OutboxEventStatus.PENDING,
                occurredAt,
                0,
                null,
                occurredAt);
    }

    public OutboxEvent {
        if (eventId == null) {
            throw new IllegalArgumentException("Event id is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Event type is required");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("Schema version must be positive");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("Aggregate id is required");
        }
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("Aggregate version cannot be negative");
        }
        if (occurredAt == null || correlationId == null || causationId == null) {
            throw new IllegalArgumentException("Event timestamps and correlation are required");
        }
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("Event payload must be an object");
        }
        if (status == null || availableAt == null || createdAt == null) {
            throw new IllegalArgumentException("Event state is required");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("Attempt count cannot be negative");
        }
        payload = payload.deepCopy();
    }

    @Override
    public JsonNode payload() {
        return payload.deepCopy();
    }
}
