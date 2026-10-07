package br.com.deladopara.eventing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.eventing.adapter.persistence.OutboxEventWriter;
import br.com.deladopara.eventing.domain.OutboxEvent;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** C79: the trace of the request that wrote an event continues in the consumer's effect. */
@SpringBootTest
@Import(PostgresTestContainer.class)
class TracePropagationIT {

    private final Tracer tracer;
    private final Propagator propagator;
    private final OutboxEventWriter writer;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    @Autowired
    TracePropagationIT(
            Tracer tracer,
            Propagator propagator,
            OutboxEventWriter writer,
            JdbcTemplate jdbc,
            TransactionTemplate tx,
            ObjectMapper objectMapper) {
        this.tracer = tracer;
        this.propagator = propagator;
        this.writer = writer;
        this.jdbc = jdbc;
        this.tx = tx;
        this.objectMapper = objectMapper;
    }

    private OutboxEvent event() {
        var id = UUID.randomUUID();
        return new OutboxEvent(
                id,
                "test.traced",
                1,
                "aggregate-" + id,
                0,
                Instant.now(),
                id,
                id,
                objectMapper.createObjectNode().put("n", 1));
    }

    private String storedTraceParent(UUID eventId) {
        return jdbc.queryForObject("SELECT trace_parent FROM event_outbox WHERE event_id = ?", String.class, eventId);
    }

    @Test
    void outboxKeepsTheRequestTraceAndTheConsumerContinuesIt() {
        var request = tracer.nextSpan().name("http.request").start();
        var event = event();
        try (var ignored = tracer.withSpan(request)) {
            tx.executeWithoutResult(status -> writer.append(event));
        } finally {
            request.end();
        }

        var traceParent = storedTraceParent(event.eventId());
        assertThat(traceParent)
                .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]")
                .contains(request.context().traceId())
                .contains(request.context().spanId());

        var record = new ConsumerRecord<String, String>("events", 0, 0L, event.aggregateId(), "{}");
        record.headers().add("traceparent", traceParent.getBytes(StandardCharsets.US_ASCII));
        var effect = new ConsumerTracing(tracer, propagator)
                .inSpan(record, event.eventType(), () -> tracer.currentSpan().context());

        assertThat(effect.traceId()).isEqualTo(request.context().traceId());
        assertThat(effect.spanId()).isNotEqualTo(request.context().spanId());
    }

    @Test
    void eventsWrittenOutsideATraceCarryNoContextAndConsumeWithoutOne() {
        var event = event();
        tx.executeWithoutResult(status -> writer.append(event));

        assertThat(storedTraceParent(event.eventId())).isNull();
        var record = new ConsumerRecord<String, String>("events", 0, 1L, event.aggregateId(), "{}");
        var ran = new ConsumerTracing(tracer, propagator).inSpan(record, event.eventType(), () -> "done");
        assertThat(ran).isEqualTo("done");
    }
}
