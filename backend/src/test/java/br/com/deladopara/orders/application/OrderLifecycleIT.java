package br.com.deladopara.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import br.com.deladopara.orders.domain.OrderTransitions.InvalidOrderTransitionException;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresTestContainer.class)
class OrderLifecycleIT {

    private final OrderService service;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Autowired
    OrderLifecycleIT(OrderService service, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.service = service;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("ALTER TABLE purchase_order_item DISABLE TRIGGER purchase_order_item_immutable");
        jdbc.execute("ALTER TABLE purchase_order_status_history DISABLE TRIGGER purchase_order_history_immutable");
        jdbc.execute("ALTER TABLE purchase_order DISABLE TRIGGER purchase_order_snapshot_guard");
        jdbc.execute(
                "TRUNCATE purchase_order_status_history, purchase_order_item, purchase_order, event_outbox CASCADE");
        jdbc.execute("ALTER TABLE purchase_order_item ENABLE TRIGGER purchase_order_item_immutable");
        jdbc.execute("ALTER TABLE purchase_order_status_history ENABLE TRIGGER purchase_order_history_immutable");
        jdbc.execute("ALTER TABLE purchase_order ENABLE TRIGGER purchase_order_snapshot_guard");
    }

    private CreateOrderCommand command(String key, FulfillmentMode mode) {
        var destination = objectMapper.createObjectNode().put("label", "Ponto de demonstração — Belém");
        return new CreateOrderCommand(
                key,
                null,
                "ana@example.com",
                mode,
                4_500,
                750,
                "PERCENTAGE",
                10L,
                450,
                "BEMVINDO",
                4_800,
                1,
                mode == FulfillmentMode.DELIVERY ? 5 : null,
                "pricing-v1",
                destination,
                List.of(
                        new CreateOrderCommand.Item(UUID.randomUUID(), "Farinha", "500 g", 2, 1_800, 3_600),
                        new CreateOrderCommand.Item(UUID.randomUUID(), "Castanha", "200 g", 1, 900, 900)),
                UUID.randomUUID());
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM purchase_order WHERE id = ?", String.class, id);
    }

    private int outbox(String type) {
        return jdbc.queryForObject("SELECT count(*) FROM event_outbox WHERE event_type = ?", Integer.class, type);
    }

    @Test
    void createsSnapshotWithInitialHistoryAndOutboxEventAndIsIdempotentByCheckoutKey() {
        var first = service.create(command("chk-1", FulfillmentMode.DELIVERY));
        var replay = service.create(command("chk-1", FulfillmentMode.DELIVERY));

        assertThat(first.created()).isTrue();
        assertThat(replay.created()).isFalse();
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(status(first.id())).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_order_item", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_order_status_history", Integer.class))
                .isEqualTo(1);
        assertThat(outbox("order.created")).isEqualTo(1);
    }

    @Test
    void snapshotColumnsItemsAndHistoryAreImmutableAtTheDatabase() {
        var id = service.create(command("chk-2", FulfillmentMode.DELIVERY)).id();
        service.transition(id, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());

        assertThatThrownBy(
                        () -> jdbc.update("UPDATE purchase_order SET total_cents = total_cents + 1 WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE purchase_order SET contact_email = 'x@y.z' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM purchase_order WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE purchase_order_item SET quantity = 9 WHERE order_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM purchase_order_item WHERE order_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() ->
                        jdbc.update("UPDATE purchase_order_status_history SET reason = 'x' WHERE order_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM purchase_order_status_history WHERE order_id = ?", id))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void databaseRejectsInconsistentTotals() {
        var bad = command("chk-3", FulfillmentMode.DELIVERY);
        var wrong = new CreateOrderCommand(
                bad.checkoutKey(),
                null,
                bad.contactEmail(),
                bad.mode(),
                4_500,
                750,
                null,
                null,
                0,
                null,
                9_999,
                1,
                5,
                "pricing-v1",
                bad.destination(),
                bad.items(),
                bad.correlationId());

        assertThatThrownBy(() -> service.create(wrong)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void walksTheDeliveryPathWithContiguousSequenceAndOneEventPerStep() {
        var id = service.create(command("chk-4", FulfillmentMode.DELIVERY)).id();
        service.transition(id, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());
        service.transition(id, OrderStatus.PREPARING, OrderActor.ADMIN, null, UUID.randomUUID());
        service.transition(id, OrderStatus.IN_TRANSIT, OrderActor.SYSTEM, null, UUID.randomUUID());
        service.transition(id, OrderStatus.DELIVERED, OrderActor.SYSTEM, null, UUID.randomUUID());

        assertThat(status(id)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForList(
                        "SELECT sequence FROM purchase_order_status_history WHERE order_id = ? ORDER BY sequence",
                        Integer.class,
                        id))
                .containsExactly(0, 1, 2, 3, 4);
        assertThat(outbox("order.status_changed")).isEqualTo(4);
        assertThat(jdbc.queryForList(
                        "SELECT aggregate_version FROM event_outbox WHERE aggregate_id = ? ORDER BY aggregate_version",
                        Long.class,
                        id.toString()))
                .containsExactly(0L, 1L, 2L, 3L, 4L);
        assertThat(jdbc.queryForObject(
                        "SELECT payload::text FROM event_outbox WHERE event_type = 'order.created'", String.class))
                .doesNotContain("ana@example.com", "Belém");
    }

    @Test
    void invalidTransitionLeavesStatusHistoryAndOutboxUnchanged() {
        var id = service.create(command("chk-5", FulfillmentMode.DELIVERY)).id();

        assertThatThrownBy(
                        () -> service.transition(id, OrderStatus.DELIVERED, OrderActor.SYSTEM, null, UUID.randomUUID()))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() ->
                        service.transition(id, OrderStatus.READY_FOR_PICKUP, OrderActor.ADMIN, "x", UUID.randomUUID()))
                .isInstanceOf(InvalidOrderTransitionException.class);

        assertThat(status(id)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_order_status_history", Integer.class))
                .isEqualTo(1);
        assertThat(outbox("order.status_changed")).isZero();
    }

    @Test
    void repeatedTransitionToCurrentStatusIsANoOp() {
        var id = service.create(command("chk-6", FulfillmentMode.PICKUP)).id();
        service.transition(id, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());
        service.transition(id, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_order_status_history", Integer.class))
                .isEqualTo(2);
        assertThat(outbox("order.status_changed")).isEqualTo(1);
    }

    @Test
    void unknownOrderIsReported() {
        assertThatThrownBy(() -> service.transition(
                        UUID.randomUUID(), OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID()))
                .isInstanceOf(OrderService.OrderNotFoundException.class);
    }
}
