package br.com.deladopara.eventing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.eventing.adapter.persistence.EventConsumptionRepository;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootTest
@Import({PostgresTestContainer.class, EventConsumptionServiceIT.TestHandlerConfiguration.class})
class EventConsumptionServiceIT {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    private final EventConsumptionRepository consumptions;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Autowired
    private EventConsumptionService service;

    @Autowired
    private JdbcEventHandler handler;

    @Autowired
    EventConsumptionServiceIT(EventConsumptionRepository consumptions, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.consumptions = consumptions;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void createEffectsTable() {
        handler.setFailAfterEffect(false);
        jdbc.execute("CREATE TABLE IF NOT EXISTS event_handler_effect (event_id UUID PRIMARY KEY)");
        jdbc.execute("TRUNCATE event_handler_effect, event_consumption, event_consumer_cursor");
    }

    @Test
    void appliesEffectAndReceiptOnceAndTreatsRedeliveryAsDuplicate() {
        var event = event(0);

        assertThat(service.consume(event)).isEqualTo(EventConsumptionOutcome.APPLIED);
        assertThat(service.consume(event)).isEqualTo(EventConsumptionOutcome.DUPLICATE);
        assertThat(effectCount()).isEqualTo(1);
        assertThat(consumptions.find(handler.handlerName(), event.eventId()))
                .get()
                .extracting(receipt -> receipt.result().name())
                .isEqualTo("APPLIED");
    }

    @Test
    void storesGapAndAppliesItWhenEarlierVersionArrives() {
        var versionOne = event(1);
        var versionZero = event(0);

        assertThat(service.consume(versionOne)).isEqualTo(EventConsumptionOutcome.PENDING_ORDER);
        assertThat(effectCount()).isZero();
        assertThat(service.consume(versionZero)).isEqualTo(EventConsumptionOutcome.APPLIED);

        assertThat(effectCount()).isEqualTo(2);
        assertThat(consumptions.find(handler.handlerName(), versionOne.eventId()))
                .get()
                .extracting(receipt -> receipt.result().name())
                .isEqualTo("APPLIED");
    }

    @Test
    void rollsBackEffectAndReceiptWhenHandlerFails() {
        handler.setFailAfterEffect(true);
        var event = event(0);

        assertThatThrownBy(() -> service.consume(event)).isInstanceOf(IllegalStateException.class);

        assertThat(effectCount()).isZero();
        assertThat(consumptions.find(handler.handlerName(), event.eventId())).isEmpty();
    }

    @Test
    void rejectsUnknownEventTypeAndSchemaBeforeWritingReceipt() {
        var event = new EventEnvelope(
                UUID.randomUUID(),
                "order.created",
                2,
                "order-unknown",
                0,
                NOW.toString(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                objectMapper.createObjectNode());

        assertThatThrownBy(() -> service.consume(event)).isInstanceOf(UnsupportedEventException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_consumption", Long.class))
                .isZero();
    }

    @Test
    void returnsDuplicateForAnAlreadyAppliedAggregateVersionWithDifferentEventId() {
        var original = event(0);
        assertThat(service.consume(original)).isEqualTo(EventConsumptionOutcome.APPLIED);
        var replay = new EventEnvelope(
                UUID.randomUUID(),
                original.eventType(),
                original.schemaVersion(),
                original.aggregateId(),
                original.aggregateVersion(),
                original.occurredAt(),
                original.correlationId(),
                original.causationId(),
                original.payload());

        assertThat(service.consume(replay)).isEqualTo(EventConsumptionOutcome.DUPLICATE);
        assertThat(effectCount()).isEqualTo(1);
        assertThat(consumptions.find(handler.handlerName(), replay.eventId())).isEmpty();
    }

    @Test
    void leavesConflictingPendingAggregateVersionUnappliedAndUncommittedAtTheServiceBoundary() {
        var pending = event(2);
        var conflicting = new EventEnvelope(
                UUID.randomUUID(),
                pending.eventType(),
                pending.schemaVersion(),
                pending.aggregateId(),
                pending.aggregateVersion(),
                pending.occurredAt(),
                pending.correlationId(),
                pending.causationId(),
                pending.payload());

        assertThat(service.consume(pending)).isEqualTo(EventConsumptionOutcome.PENDING_ORDER);
        assertThatThrownBy(() -> service.consume(conflicting)).isInstanceOf(RuntimeException.class);
        assertThat(effectCount()).isZero();
        assertThat(consumptions.find(handler.handlerName(), pending.eventId())).isPresent();
        assertThat(consumptions.find(handler.handlerName(), conflicting.eventId()))
                .isEmpty();
    }

    private long effectCount() {
        return jdbc.queryForObject("SELECT count(*) FROM event_handler_effect", Long.class);
    }

    private EventEnvelope event(long version) {
        var eventId = UUID.randomUUID();
        return new EventEnvelope(
                eventId,
                "order.created",
                1,
                "order-service-it",
                version,
                NOW.toString(),
                UUID.randomUUID(),
                eventId,
                objectMapper.createObjectNode().put("orderId", "order-service-it"));
    }

    static class JdbcEventHandler implements EventHandler {

        private final JdbcTemplate jdbc;
        private boolean failAfterEffect;

        JdbcEventHandler(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        void setFailAfterEffect(boolean failAfterEffect) {
            this.failAfterEffect = failAfterEffect;
        }

        @Override
        public String handlerName() {
            return "service-it-handler";
        }

        @Override
        public String eventType() {
            return "order.created";
        }

        @Override
        public int schemaVersion() {
            return 1;
        }

        @Override
        public void handle(EventEnvelope event) {
            jdbc.update("INSERT INTO event_handler_effect (event_id) VALUES (?)", event.eventId());
            if (failAfterEffect) {
                throw new IllegalStateException("test handler failure");
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestHandlerConfiguration {

        @Bean
        EventConsumptionService eventConsumptionService(
                EventConsumptionRepository consumptions, java.util.List<EventHandler> handlers, java.time.Clock clock) {
            return new EventConsumptionService(consumptions, handlers, clock);
        }

        @Bean
        JdbcEventHandler jdbcEventHandler(JdbcTemplate jdbc) {
            return new JdbcEventHandler(jdbc);
        }
    }
}
