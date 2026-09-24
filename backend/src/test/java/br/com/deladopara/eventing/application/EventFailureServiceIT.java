package br.com.deladopara.eventing.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.eventing.adapter.persistence.EventFailureRepository.Failure;
import br.com.deladopara.support.PostgresTestContainer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresTestContainer.class)
class EventFailureServiceIT {

    private static final String TOPIC = "events.failure-it";

    @Autowired
    private EventFailureService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE event_consumer_failure");
    }

    @Test
    void transientFailureIsRetriedEightTimesThenQuarantinedWithCumulativeCount() {
        var failure = new Failure(TOPIC, 0, 3L, UUID.randomUUID(), UUID.randomUUID());
        for (var attempt = 1; attempt < 8; attempt++) {
            var decision = service.recordFailure(failure, new IllegalStateException("db said secret-token"));
            assertThat(decision.quarantined()).isFalse();
            assertThat(decision.attempts()).isEqualTo(attempt);
            assertThat(decision.nextAttemptAt()).isNotNull();
        }

        var last = service.recordFailure(failure, new IllegalStateException("db said secret-token"));

        assertThat(last.quarantined()).isTrue();
        assertThat(last.attempts()).isEqualTo(8);
        var row = jdbc.queryForMap("SELECT * FROM event_consumer_failure WHERE record_offset = 3");
        assertThat(row.get("state")).isEqualTo("QUARANTINED");
        assertThat(row.get("failure_kind")).isEqualTo("TRANSIENT");
        assertThat(row.get("next_attempt_at")).isNull();
        assertThat(row.get("quarantined_at")).isNotNull();
        assertThat(row.get("last_error")).isEqualTo("TRANSIENT:IllegalStateException");
    }

    @Test
    void invalidFailureIsQuarantinedOnFirstAttemptWithoutPayloadOrMessage() {
        var decision = service.recordFailure(
                new Failure(TOPIC, 1, 0L, null, null), new InvalidEventEnvelopeException("payload: cpf 123"));

        assertThat(decision.quarantined()).isTrue();
        assertThat(decision.attempts()).isEqualTo(1);
        var row = jdbc.queryForMap("SELECT * FROM event_consumer_failure WHERE partition_id = 1");
        assertThat(row.get("last_error")).isEqualTo("INVALID:InvalidEventEnvelopeException");
        assertThat(row.get("event_id")).isNull();
    }

    @Test
    void retryBackoffNeverExceedsTheOneMinuteCeiling() {
        var failure = new Failure(TOPIC, 2, 9L, null, null);
        var before = java.time.Instant.now();
        for (var attempt = 1; attempt < 8; attempt++) {
            var decision = service.recordFailure(failure, new IllegalStateException("x"));
            assertThat(decision.nextAttemptAt()).isBefore(before.plusSeconds(61).plusSeconds(5));
        }
    }
}
