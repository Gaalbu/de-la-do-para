package br.com.deladopara.eventing.adapter.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventFailureRepository {

    private final JdbcTemplate jdbc;

    public EventFailureRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int attemptCount(String topic, int partition, long offset) {
        return jdbc
                .query(
                        "SELECT attempt_count FROM event_consumer_failure"
                                + " WHERE topic = ? AND partition_id = ? AND record_offset = ?",
                        (rs, row) -> rs.getInt(1),
                        topic,
                        partition,
                        offset)
                .stream()
                .findFirst()
                .orElse(0);
    }

    public void recordRetry(
            Failure failure, String kind, int attempts, Instant nextAttemptAt, String lastError, Instant now) {
        jdbc.update(
                """
                INSERT INTO event_consumer_failure
                    (topic, partition_id, record_offset, failure_kind, state, attempt_count, next_attempt_at,
                     last_error, event_id, correlation_id, first_failed_at, updated_at)
                VALUES (?, ?, ?, ?, 'RETRYING', ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (topic, partition_id, record_offset) DO UPDATE SET
                    failure_kind = EXCLUDED.failure_kind, state = 'RETRYING',
                    attempt_count = EXCLUDED.attempt_count, next_attempt_at = EXCLUDED.next_attempt_at,
                    last_error = EXCLUDED.last_error,
                    event_id = COALESCE(EXCLUDED.event_id, event_consumer_failure.event_id),
                    correlation_id = COALESCE(EXCLUDED.correlation_id, event_consumer_failure.correlation_id),
                    updated_at = EXCLUDED.updated_at
                """,
                failure.topic(),
                failure.partition(),
                failure.offset(),
                kind,
                attempts,
                Timestamp.from(nextAttemptAt),
                lastError,
                failure.eventId(),
                failure.correlationId(),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public void recordQuarantine(Failure failure, String kind, int attempts, String lastError, Instant now) {
        jdbc.update(
                """
                INSERT INTO event_consumer_failure
                    (topic, partition_id, record_offset, failure_kind, state, attempt_count, next_attempt_at,
                     last_error, event_id, correlation_id, first_failed_at, updated_at, quarantined_at)
                VALUES (?, ?, ?, ?, 'QUARANTINED', ?, NULL, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (topic, partition_id, record_offset) DO UPDATE SET
                    failure_kind = EXCLUDED.failure_kind, state = 'QUARANTINED',
                    attempt_count = EXCLUDED.attempt_count, next_attempt_at = NULL,
                    last_error = EXCLUDED.last_error,
                    event_id = COALESCE(EXCLUDED.event_id, event_consumer_failure.event_id),
                    correlation_id = COALESCE(EXCLUDED.correlation_id, event_consumer_failure.correlation_id),
                    updated_at = EXCLUDED.updated_at, quarantined_at = EXCLUDED.quarantined_at
                """,
                failure.topic(),
                failure.partition(),
                failure.offset(),
                kind,
                attempts,
                lastError,
                failure.eventId(),
                failure.correlationId(),
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public Optional<String> stateOf(String topic, int partition, long offset) {
        return jdbc
                .query(
                        "SELECT state FROM event_consumer_failure"
                                + " WHERE topic = ? AND partition_id = ? AND record_offset = ?",
                        (rs, row) -> rs.getString(1),
                        topic,
                        partition,
                        offset)
                .stream()
                .findFirst();
    }

    /** Identity of the failed Kafka record; event ids are only known once the envelope validated. */
    public record Failure(String topic, int partition, long offset, UUID eventId, UUID correlationId) {}
}
