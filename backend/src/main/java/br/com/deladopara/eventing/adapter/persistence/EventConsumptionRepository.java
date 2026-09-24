package br.com.deladopara.eventing.adapter.persistence;

import br.com.deladopara.eventing.application.EventEnvelope;
import br.com.deladopara.eventing.domain.EventConsumptionRecord;
import br.com.deladopara.eventing.domain.EventConsumptionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EventConsumptionRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public EventConsumptionRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean insertPending(String handlerName, EventEnvelope event, Instant receivedAt) {
        return jdbc.update(
                        """
                        INSERT INTO event_consumption
                            (event_id, handler_name, event_type, schema_version, aggregate_id,
                             aggregate_version, occurred_at, correlation_id, causation_id, payload,
                             processing_result, received_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'PENDING_ORDER', ?)
                        ON CONFLICT (event_id, handler_name) DO NOTHING
                        """,
                        event.eventId(),
                        handlerName,
                        event.eventType(),
                        event.schemaVersion(),
                        event.aggregateId(),
                        event.aggregateVersion(),
                        Timestamp.from(Instant.parse(event.occurredAt())),
                        event.correlationId(),
                        event.causationId(),
                        payloadJson(event.payload()),
                        Timestamp.from(receivedAt))
                == 1;
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<EventConsumptionRecord> find(String handlerName, UUID eventId) {
        var rows = jdbc.query(
                "SELECT * FROM event_consumption WHERE handler_name = ? AND event_id = ?",
                (resultSet, rowNumber) -> map(resultSet),
                handlerName,
                eventId);
        return rows.stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long lockCursor(String handlerName, String aggregateId) {
        jdbc.update(
                "INSERT INTO event_consumer_cursor (handler_name, aggregate_id) VALUES (?, ?) "
                        + "ON CONFLICT (handler_name, aggregate_id) DO NOTHING",
                handlerName,
                aggregateId);
        return jdbc.queryForObject(
                "SELECT last_aggregate_version FROM event_consumer_cursor "
                        + "WHERE handler_name = ? AND aggregate_id = ? FOR UPDATE",
                Long.class,
                handlerName,
                aggregateId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<EventConsumptionRecord> findPendingVersion(String handlerName, String aggregateId, long version) {
        var rows = jdbc.query(
                "SELECT * FROM event_consumption WHERE handler_name = ? AND aggregate_id = ? "
                        + "AND aggregate_version = ? AND processing_result = 'PENDING_ORDER' FOR UPDATE",
                (resultSet, rowNumber) -> map(resultSet),
                handlerName,
                aggregateId,
                version);
        return rows.stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markApplied(String handlerName, UUID eventId, Instant appliedAt) {
        var changed = jdbc.update(
                "UPDATE event_consumption SET processing_result = 'APPLIED', applied_at = ? "
                        + "WHERE handler_name = ? AND event_id = ? AND processing_result = 'PENDING_ORDER'",
                Timestamp.from(appliedAt),
                handlerName,
                eventId);
        if (changed != 1) {
            throw new IllegalStateException("Pending event receipt was not updated");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void advanceCursor(
            String handlerName, String aggregateId, long expectedVersion, long newVersion, Instant updatedAt) {
        var changed = jdbc.update(
                "UPDATE event_consumer_cursor SET last_aggregate_version = ?, updated_at = ? "
                        + "WHERE handler_name = ? AND aggregate_id = ? AND last_aggregate_version = ?",
                newVersion,
                Timestamp.from(updatedAt),
                handlerName,
                aggregateId,
                expectedVersion);
        if (changed != 1) {
            throw new IllegalStateException("Aggregate consumer cursor changed unexpectedly");
        }
    }

    private EventConsumptionRecord map(ResultSet row) throws SQLException {
        try {
            JsonNode payload = objectMapper.readTree(row.getString("payload"));
            var occurredAt = row.getObject("occurred_at", OffsetDateTime.class).toInstant();
            var envelope = new EventEnvelope(
                    row.getObject("event_id", UUID.class),
                    row.getString("event_type"),
                    row.getInt("schema_version"),
                    row.getString("aggregate_id"),
                    row.getLong("aggregate_version"),
                    occurredAt.toString(),
                    row.getObject("correlation_id", UUID.class),
                    row.getObject("causation_id", UUID.class),
                    payload);
            return new EventConsumptionRecord(
                    envelope,
                    EventConsumptionResult.valueOf(row.getString("processing_result")),
                    row.getObject("received_at", OffsetDateTime.class).toInstant(),
                    Optional.ofNullable(row.getObject("applied_at", OffsetDateTime.class))
                            .map(OffsetDateTime::toInstant)
                            .orElse(null));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored event payload is invalid JSON", exception);
        }
    }

    private String payloadJson(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Event payload cannot be serialized", exception);
        }
    }
}
