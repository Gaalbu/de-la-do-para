package br.com.deladopara.eventing.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.support.PostgresTestContainer;
import java.sql.Timestamp;
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

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    @Autowired
    EventConsumptionPersistenceIT(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Test
    void storesOneConsumptionReceiptPerEventAndHandler() {
        var eventId = UUID.randomUUID();
        var handlerName = "test-order-handler";
        var inserted = transactions.execute(status -> insertReceipt(eventId, handlerName));
        var duplicate = transactions.execute(status -> insertReceipt(eventId, handlerName));

        assertThat(inserted).isEqualTo(1);
        assertThat(duplicate).isZero();
        assertThat(receiptCount(eventId, handlerName)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT processing_result FROM event_consumption WHERE event_id = ? AND handler_name = ?",
                        String.class,
                        eventId,
                        handlerName))
                .isEqualTo("PENDING_ORDER");
        assertThat(jdbc.queryForObject(
                        "SELECT payload ->> 'orderId' FROM event_consumption WHERE event_id = ? AND handler_name = ?",
                        String.class,
                        eventId,
                        handlerName))
                .isEqualTo("order-1");
    }

    @Test
    void keepsTheSameEventAvailableToAnotherHandler() {
        var eventId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            insertReceipt(eventId, "billing-handler");
            insertReceipt(eventId, "analytics-handler");
        });

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM event_consumption WHERE event_id = ?", Integer.class, eventId))
                .isEqualTo(2);
    }

    @Test
    void initializesAggregateCursorBeforeTheFirstVersion() {
        var handlerName = "test-order-handler";
        var aggregateId = "order-1";

        var inserted = transactions.execute(status -> insertCursor(handlerName, aggregateId));
        var duplicate = transactions.execute(status -> insertCursor(handlerName, aggregateId));

        assertThat(inserted).isEqualTo(1);
        assertThat(duplicate).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT last_aggregate_version FROM event_consumer_cursor "
                                + "WHERE handler_name = ? AND aggregate_id = ?",
                        Long.class,
                        handlerName,
                        aggregateId))
                .isEqualTo(-1L);
    }

    @Test
    void rollsBackAConsumptionReceiptWithTheHandlerTransaction() {
        var eventId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            insertReceipt(eventId, "test-order-handler");
            status.setRollbackOnly();
        });

        assertThat(receiptCount(eventId, "test-order-handler")).isZero();
    }

    private int insertReceipt(UUID eventId, String handlerName) {
        return jdbc.update(
                """
                INSERT INTO event_consumption
                    (event_id, handler_name, event_type, schema_version, aggregate_id,
                     aggregate_version, occurred_at, correlation_id, payload, processing_result, received_at)
                VALUES (?, ?, 'order.created', 1, 'order-1', 0, ?, ?, '{\"orderId\":\"order-1\"}'::jsonb,
                        'PENDING_ORDER', ?)
                ON CONFLICT (event_id, handler_name) DO NOTHING
                """,
                eventId,
                handlerName,
                Timestamp.from(Instant.parse("2026-09-24T11:59:00Z")),
                eventId,
                Timestamp.from(Instant.parse("2026-09-24T12:00:00Z")));
    }

    private Integer receiptCount(UUID eventId, String handlerName) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_consumption WHERE event_id = ? AND handler_name = ?",
                Integer.class,
                eventId,
                handlerName);
    }

    private int insertCursor(String handlerName, String aggregateId) {
        return jdbc.update("""
                INSERT INTO event_consumer_cursor (handler_name, aggregate_id)
                VALUES (?, ?)
                ON CONFLICT (handler_name, aggregate_id) DO NOTHING
                """, handlerName, aggregateId);
    }
}
