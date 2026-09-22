package br.com.deladopara.eventing.adapter.persistence;

import br.com.deladopara.eventing.domain.OutboxEvent;
import br.com.deladopara.eventing.domain.OutboxEventStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "event_outbox")
public class OutboxEventEntity {

    @Id
    private UUID eventId;

    @Column(nullable = false, length = 160)
    private String eventType;

    @Column(nullable = false)
    private int schemaVersion;

    @Column(nullable = false, length = 160)
    private String aggregateId;

    @Column(nullable = false)
    private long aggregateVersion;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private UUID correlationId;

    @Column(nullable = false)
    private UUID causationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private Instant availableAt;

    @Column(nullable = false)
    private int attemptCount;

    private Instant leaseUntil;

    @Column(columnDefinition = "text")
    private String lastError;

    private Instant publishedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected OutboxEventEntity() {}

    public OutboxEventEntity(OutboxEvent event) {
        this.eventId = event.eventId();
        this.eventType = event.eventType();
        this.schemaVersion = event.schemaVersion();
        this.aggregateId = event.aggregateId();
        this.aggregateVersion = event.aggregateVersion();
        this.occurredAt = event.occurredAt();
        this.correlationId = event.correlationId();
        this.causationId = event.causationId();
        this.payload = event.payload();
        this.status = event.status();
        this.availableAt = event.availableAt();
        this.attemptCount = event.attemptCount();
        this.leaseUntil = event.leaseUntil();
        this.createdAt = event.createdAt();
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public JsonNode getPayload() {
        return payload.deepCopy();
    }

    public void claim(Instant newLeaseUntil) {
        this.leaseUntil = newLeaseUntil;
        this.attemptCount++;
    }

    public OutboxEvent toDomain() {
        return new OutboxEvent(
                eventId,
                eventType,
                schemaVersion,
                aggregateId,
                aggregateVersion,
                occurredAt,
                correlationId,
                causationId,
                payload,
                status,
                availableAt,
                attemptCount,
                leaseUntil,
                createdAt);
    }
}
