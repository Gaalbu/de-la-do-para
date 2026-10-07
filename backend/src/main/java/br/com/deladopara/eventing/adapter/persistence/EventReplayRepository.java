package br.com.deladopara.eventing.adapter.persistence;

import br.com.deladopara.eventing.application.EventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Quarantined records, the replay requests made for them and the outbox envelopes they replay. */
@Repository
public class EventReplayRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public EventReplayRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Quarantined records, newest first, each with its latest replay request if any. */
    public List<QuarantinedRecord> quarantined(int limit) {
        return jdbc.query(
                """
                SELECT f.topic, f.partition_id, f.record_offset, f.failure_kind, f.attempt_count, f.last_error,
                       f.event_id, f.correlation_id, f.quarantined_at,
                       r.status AS replay_status, r.result AS replay_result, r.actor AS replay_actor,
                       r.requested_at AS replay_requested_at
                FROM event_consumer_failure f
                LEFT JOIN LATERAL (SELECT * FROM event_replay_request q
                                   WHERE q.topic = f.topic AND q.partition_id = f.partition_id
                                     AND q.record_offset = f.record_offset
                                   ORDER BY q.requested_at DESC, q.id LIMIT 1) r ON true
                WHERE f.state = 'QUARANTINED'
                ORDER BY f.quarantined_at DESC, f.topic, f.partition_id, f.record_offset
                LIMIT ?
                """,
                (rs, row) -> new QuarantinedRecord(
                        rs.getString("topic"),
                        rs.getInt("partition_id"),
                        rs.getLong("record_offset"),
                        rs.getString("failure_kind"),
                        rs.getInt("attempt_count"),
                        rs.getString("last_error"),
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("correlation_id", UUID.class),
                        instant(rs, "quarantined_at"),
                        rs.getString("replay_status"),
                        rs.getString("replay_result"),
                        rs.getString("replay_actor"),
                        instant(rs, "replay_requested_at")),
                limit);
    }

    /** Locks the quarantined record, so concurrent requests for it are decided one after the other. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> lockQuarantinedEventId(String topic, int partition, long offset) {
        return jdbc
                .query(
                        "SELECT event_id FROM event_consumer_failure WHERE topic = ? AND partition_id = ?"
                                + " AND record_offset = ? AND state = 'QUARANTINED' FOR UPDATE",
                        (rs, row) -> Optional.ofNullable(rs.getObject("event_id", UUID.class)),
                        topic,
                        partition,
                        offset)
                .stream()
                .findFirst()
                .orElse(Optional.empty());
    }

    public boolean quarantinedExists(String topic, int partition, long offset) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM event_consumer_failure WHERE topic = ? AND partition_id = ?"
                        + " AND record_offset = ? AND state = 'QUARANTINED')",
                Boolean.class,
                topic,
                partition,
                offset));
    }

    public boolean outboxHas(UUID eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM event_outbox WHERE event_id = ?)", Boolean.class, eventId));
    }

    public Optional<UUID> pendingRequest(String topic, int partition, long offset) {
        return jdbc
                .queryForList(
                        "SELECT id FROM event_replay_request WHERE topic = ? AND partition_id = ?"
                                + " AND record_offset = ? AND status = 'PENDING'",
                        UUID.class,
                        topic,
                        partition,
                        offset)
                .stream()
                .findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertRequest(
            UUID id, String topic, int partition, long offset, UUID eventId, String actor, String reason, Instant now) {
        jdbc.update(
                "INSERT INTO event_replay_request (id, topic, partition_id, record_offset, event_id, actor, reason,"
                        + " status, requested_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)",
                id,
                topic,
                partition,
                offset,
                eventId,
                actor,
                reason,
                Timestamp.from(now));
    }

    /** Locks the oldest pending request, skipping one another worker is replaying. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PendingReplay> lockOldestPending() {
        return jdbc
                .query(
                        "SELECT id, event_id FROM event_replay_request WHERE status = 'PENDING'"
                                + " ORDER BY requested_at, id LIMIT 1 FOR UPDATE SKIP LOCKED",
                        (rs, row) ->
                                new PendingReplay(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class)))
                .stream()
                .findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void finish(UUID id, String status, String result, Instant now) {
        jdbc.update(
                "UPDATE event_replay_request SET status = ?, result = ?, finished_at = ?"
                        + " WHERE id = ? AND status = 'PENDING'",
                status,
                result,
                Timestamp.from(now),
                id);
    }

    /** The envelope exactly as it was published: same id, version and correlation. */
    public Optional<EventEnvelope> outboxEnvelope(UUID eventId) {
        return jdbc
                .query(
                        "SELECT * FROM event_outbox WHERE event_id = ?",
                        (rs, row) -> new EventEnvelope(
                                rs.getObject("event_id", UUID.class),
                                rs.getString("event_type"),
                                rs.getInt("schema_version"),
                                rs.getString("aggregate_id"),
                                rs.getLong("aggregate_version"),
                                rs.getTimestamp("occurred_at").toInstant().toString(),
                                rs.getObject("correlation_id", UUID.class),
                                rs.getObject("causation_id", UUID.class),
                                json(rs.getString("payload"))),
                        eventId)
                .stream()
                .findFirst();
    }

    private com.fasterxml.jackson.databind.JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored outbox payload is not valid JSON", e);
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record QuarantinedRecord(
            String topic,
            int partition,
            long offset,
            String failureKind,
            int attempts,
            String lastError,
            UUID eventId,
            UUID correlationId,
            Instant quarantinedAt,
            String replayStatus,
            String replayResult,
            String replayRequestedBy,
            Instant replayRequestedAt) {}

    public record PendingReplay(UUID id, UUID eventId) {}
}
