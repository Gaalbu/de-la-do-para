package br.com.deladopara.eventing.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.eventing.application.EventEnvelope;
import br.com.deladopara.eventing.domain.EventConsumptionResult;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(PostgresTestContainer.class)
class EventConsumptionPersistenceIT {

    private static final Instant RECEIVED_AT = Instant.parse("2026-09-24T12:00:00Z");

    private final EventConsumptionRepository consumptions;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    @Autowired
    EventConsumptionPersistenceIT(
            EventConsumptionRepository consumptions,
            TransactionTemplate transactions,
            ObjectMapper objectMapper,
            JdbcTemplate jdbc) {
        this.consumptions = consumptions;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @Test
    void storesOneConsumptionReceiptPerEventAndHandler() {
        var eventId = UUID.randomUUID();
        var handlerName = "test-order-handler";
        var event = event(eventId);
        var inserted = transactions.execute(status -> consumptions.insertPending(handlerName, event, RECEIVED_AT));
        var duplicate = transactions.execute(status -> consumptions.insertPending(handlerName, event, RECEIVED_AT));

        assertThat(inserted).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(consumptions.find(handlerName, eventId)).get().satisfies(receipt -> {
            assertThat(receipt.result()).isEqualTo(EventConsumptionResult.PENDING_ORDER);
            assertThat(receipt.envelope().payload().get("orderId").asText()).isEqualTo("order-1");
            assertThat(receipt.receivedAt()).isEqualTo(RECEIVED_AT);
            assertThat(receipt.envelope().causationId()).isEqualTo(eventId);
        });
    }

    @Test
    void keepsTheSameEventAvailableToAnotherHandler() {
        var eventId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            consumptions.insertPending("billing-handler", event(eventId), RECEIVED_AT);
            consumptions.insertPending("analytics-handler", event(eventId), RECEIVED_AT);
        });

        assertThat(consumptions.find("billing-handler", eventId)).isPresent();
        assertThat(consumptions.find("analytics-handler", eventId)).isPresent();
    }

    @Test
    void initializesAggregateCursorBeforeTheFirstVersion() {
        var handlerName = "test-order-handler";
        var aggregateId = "order-1";

        var firstVersion = transactions.execute(status -> consumptions.lockCursor(handlerName, aggregateId));
        var secondVersion = transactions.execute(status -> consumptions.lockCursor(handlerName, aggregateId));

        assertThat(firstVersion).isEqualTo(-1L);
        assertThat(secondVersion).isEqualTo(-1L);
    }

    @Test
    void rollsBackAConsumptionReceiptWithTheHandlerTransaction() {
        var eventId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            consumptions.insertPending("test-order-handler", event(eventId), RECEIVED_AT);
            status.setRollbackOnly();
        });

        assertThat(consumptions.find("test-order-handler", eventId)).isEmpty();
    }

    @Test
    void serializesDifferentEventsForOneAggregateCursor() {
        var handlerName = "cursor-concurrency-" + UUID.randomUUID();
        var aggregateId = "order-" + UUID.randomUUID();

        var first = transactions.execute(status -> consumptions.lockCursor(handlerName, aggregateId));
        transactions.executeWithoutResult(
                status -> consumptions.advanceCursor(handlerName, aggregateId, first, 0, RECEIVED_AT));
        var next = transactions.execute(status -> consumptions.lockCursor(handlerName, aggregateId));

        assertThat(first).isEqualTo(-1L);
        assertThat(next).isZero();
    }

    @Test
    void rejectsTwoPendingReceiptsForTheSameAggregateVersion() {
        var handlerName = "unique-version-" + UUID.randomUUID();
        var aggregateId = "order-" + UUID.randomUUID();
        var first = event(UUID.randomUUID(), aggregateId, 0);
        var second = event(UUID.randomUUID(), aggregateId, 0);

        transactions.executeWithoutResult(status -> consumptions.insertPending(handlerName, first, RECEIVED_AT));
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                        status -> consumptions.insertPending(handlerName, second, RECEIVED_AT)))
                .hasMessageContaining("event_consumption_pending_version_key");
    }

    @Test
    void pendingVersionLookupReturnsTheReceiptToApplyNext() {
        var handlerName = "pending-lookup-" + UUID.randomUUID();
        var aggregateId = "order-" + UUID.randomUUID();
        var event = event(UUID.randomUUID(), aggregateId, 0);
        transactions.executeWithoutResult(status -> consumptions.insertPending(handlerName, event, RECEIVED_AT));

        var pending = transactions.execute(status -> consumptions.findPendingVersion(handlerName, aggregateId, 0));

        assertThat(pending)
                .get()
                .extracting(receipt -> receipt.envelope().eventId())
                .isEqualTo(event.eventId());
    }

    private EventEnvelope event(UUID eventId) {
        return event(eventId, "order-1", 0);
    }

    private EventEnvelope event(UUID eventId, String aggregateId, long aggregateVersion) {
        return new EventEnvelope(
                eventId,
                "order.created",
                1,
                aggregateId,
                aggregateVersion,
                "2026-09-24T11:59:00Z",
                eventId,
                eventId,
                objectMapper.createObjectNode().put("orderId", "order-1"));
    }
}
