package br.com.deladopara.eventing.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.deladopara.eventing.application.EventEnvelope;
import br.com.deladopara.eventing.application.EventHandler;
import br.com.deladopara.eventing.application.EventReplayService;
import br.com.deladopara.support.PostgresTestContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** C82: audited replay of quarantined records keeps the event identity and the handlers' own rules. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainer.class, EventReplayIT.ReplayHandlerConfiguration.class})
class EventReplayIT {

    private static final String ADMIN = "admin@deladopara.local";
    private static final String TYPE = "test.replay.happened";
    private static final String TOPIC = "events.inbound";

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final EventReplayService replays;
    private final RecordingHandler handler;

    @Autowired
    EventReplayIT(MockMvc mvc, JdbcTemplate jdbc, EventReplayService replays, RecordingHandler handler) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.replays = replays;
        this.handler = handler;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE event_replay_request, event_consumer_failure, event_consumption,"
                + " event_consumer_cursor, event_outbox CASCADE");
        handler.reset();
    }

    /** An event published by the outbox whose consumption ended in quarantine at {@code offset}. */
    private Quarantined quarantined(long offset, boolean withEventId) {
        var eventId = UUID.randomUUID();
        var correlationId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO event_outbox (event_id, event_type, schema_version, aggregate_id, aggregate_version,"
                        + " occurred_at, correlation_id, causation_id, payload, status, available_at, published_at,"
                        + " created_at) VALUES (?, ?, 1, ?, 0, now(), ?, ?, '{\"n\":1}'::jsonb, 'PUBLISHED', now(),"
                        + " now(), now())",
                eventId,
                TYPE,
                "aggregate-" + eventId,
                correlationId,
                correlationId);
        jdbc.update(
                "INSERT INTO event_consumer_failure (topic, partition_id, record_offset, failure_kind, state,"
                        + " attempt_count, last_error, event_id, correlation_id, first_failed_at, updated_at,"
                        + " quarantined_at) VALUES (?, 0, ?, 'TRANSIENT', 'QUARANTINED', 8, 'IllegalStateException',"
                        + " ?, ?, now(), now(), now())",
                TOPIC,
                offset,
                withEventId ? eventId : null,
                correlationId);
        return new Quarantined(offset, eventId, correlationId);
    }

    private ResultActions requestReplay(long offset, String reason) throws Exception {
        return mvc.perform(post("/api/v1/admin/events/quarantine/" + TOPIC + "/0/" + offset + "/replays")
                .with(user(ADMIN).roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}"));
    }

    private List<String> requestStatuses(long offset) {
        return jdbc.queryForList(
                "SELECT status FROM event_replay_request WHERE record_offset = ? ORDER BY requested_at, id",
                String.class,
                offset);
    }

    @Test
    void replayConsumesTheOriginalEnvelopeOnceAndARepeatIsADuplicate() throws Exception {
        var record = quarantined(7, true);

        requestReplay(7, "Banco voltou")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.created").value(true));
        requestReplay(7, "Banco voltou mesmo")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.created").value(false));

        assertThat(replays.replayNext()).isTrue();
        assertThat(replays.replayNext()).isFalse();
        assertThat(handler.seen()).containsExactly(record.eventId() + "@" + record.correlationId());
        assertThat(requestStatuses(7)).containsExactly("APPLIED");

        requestReplay(7, "Conferir de novo").andExpect(jsonPath("$.created").value(true));
        replays.replayNext();

        assertThat(handler.seen()).hasSize(1);
        assertThat(requestStatuses(7)).containsExactly("APPLIED", "DUPLICATE");
        mvc.perform(get("/api/v1/admin/events/quarantine").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offset").value(7))
                .andExpect(jsonPath("$[0].eventId").value(record.eventId().toString()))
                .andExpect(jsonPath("$[0].replayStatus").value("DUPLICATE"))
                .andExpect(jsonPath("$[0].replayRequestedBy").value(ADMIN));
    }

    @Test
    void failingHandlerRollsBackAndClosesTheRequestAsFailed() throws Exception {
        quarantined(8, true);
        handler.failing.set(true);

        requestReplay(8, "Tentar após correção").andExpect(status().isAccepted());
        assertThat(replays.replayNext()).isTrue();

        assertThat(requestStatuses(8)).containsExactly("FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_consumption", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT result FROM event_replay_request WHERE record_offset = 8", String.class))
                .isEqualTo("FAILED:IllegalStateException");
    }

    @Test
    void onlyReadableQuarantinedRecordsAdminsCsrfAndAReasonAreAccepted() throws Exception {
        quarantined(9, false);
        var gone = quarantined(10, true);
        jdbc.update("DELETE FROM event_outbox WHERE event_id = ?", gone.eventId());
        quarantined(11, true);

        requestReplay(9, "Sem id")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EVENTING_002"));
        requestReplay(10, "Fora da outbox").andExpect(status().isConflict());
        requestReplay(99, "Inexistente")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("EVENTING_001"));
        requestReplay(11, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("EVENTING_003"));
        mvc.perform(post("/api/v1/admin/events/quarantine/" + TOPIC + "/0/11/replays")
                        .with(user("cliente@example.com").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/events/quarantine/" + TOPIC + "/0/11/replays")
                        .with(user(ADMIN).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_replay_request", Integer.class))
                .isZero();
    }

    private record Quarantined(long offset, UUID eventId, UUID correlationId) {}

    /** Records each applied event with the correlation seen in the MDC; can be told to fail. */
    static class RecordingHandler implements EventHandler {

        private final List<String> seen = new ArrayList<>();
        final AtomicBoolean failing = new AtomicBoolean();

        synchronized List<String> seen() {
            return List.copyOf(seen);
        }

        synchronized void reset() {
            seen.clear();
            failing.set(false);
        }

        @Override
        public String handlerName() {
            return "replay-it";
        }

        @Override
        public String eventType() {
            return TYPE;
        }

        @Override
        public int schemaVersion() {
            return 1;
        }

        @Override
        public synchronized void handle(EventEnvelope event) {
            if (failing.get()) {
                throw new IllegalStateException("handler still failing");
            }
            seen.add(event.eventId() + "@" + MDC.get("correlationId"));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ReplayHandlerConfiguration {

        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }
    }
}
