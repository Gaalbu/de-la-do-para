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
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresTestContainer.class)
class PickupLifecycleTest {

    private final OrderService orders;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Autowired
    PickupLifecycleTest(OrderService orders, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.orders = orders;
        this.jdbc = jdbc;
        this.mapper = mapper;
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

    @Test
    void paidPickupCanBePreparedMadeReadyAndConfirmedExactlyOnce() {
        var id = orders.create(command("pickup-1", FulfillmentMode.PICKUP)).id();
        orders.transition(id, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());
        orders.transition(id, OrderStatus.PREPARING, OrderActor.ADMIN, null, UUID.randomUUID());
        assertThat(orders.transition(id, OrderStatus.READY_FOR_PICKUP, OrderActor.ADMIN, null, UUID.randomUUID()))
                .isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(orders.transition(id, OrderStatus.READY_FOR_PICKUP, OrderActor.ADMIN, null, UUID.randomUUID()))
                .isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(orders.transition(id, OrderStatus.PICKED_UP, OrderActor.ADMIN, null, UUID.randomUUID()))
                .isEqualTo(OrderStatus.PICKED_UP);
        assertThat(orders.transition(id, OrderStatus.PICKED_UP, OrderActor.ADMIN, null, UUID.randomUUID()))
                .isEqualTo(OrderStatus.PICKED_UP);

        assertThat(status(id)).isEqualTo("PICKED_UP");
        assertThat(jdbc.queryForList(
                        "SELECT to_status FROM purchase_order_status_history WHERE order_id = ? ORDER BY sequence",
                        String.class,
                        id))
                .containsExactly("PENDING_PAYMENT", "PAID", "PREPARING", "READY_FOR_PICKUP", "PICKED_UP");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM purchase_order_status_history WHERE order_id = ?", Integer.class, id))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM event_outbox WHERE event_type = 'order.status_changed' AND aggregate_id = ?",
                        Integer.class,
                        id.toString()))
                .isEqualTo(4);
    }

    @Test
    void pickupCannotBeMarkedReadyBeforePaymentOrForDeliveryOrders() {
        var unpaid =
                orders.create(command("pickup-unpaid", FulfillmentMode.PICKUP)).id();
        assertThatThrownBy(() -> orders.transition(
                        unpaid, OrderStatus.READY_FOR_PICKUP, OrderActor.ADMIN, null, UUID.randomUUID()))
                .isInstanceOf(InvalidOrderTransitionException.class);

        var delivery =
                orders.create(command("delivery-1", FulfillmentMode.DELIVERY)).id();
        orders.transition(delivery, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());
        orders.transition(delivery, OrderStatus.PREPARING, OrderActor.ADMIN, null, UUID.randomUUID());
        assertThatThrownBy(() -> orders.transition(
                        delivery, OrderStatus.READY_FOR_PICKUP, OrderActor.ADMIN, null, UUID.randomUUID()))
                .isInstanceOf(InvalidOrderTransitionException.class);

        assertThat(status(unpaid)).isEqualTo("PENDING_PAYMENT");
        assertThat(status(delivery)).isEqualTo("PREPARING");
    }

    private CreateOrderCommand command(String key, FulfillmentMode mode) {
        return new CreateOrderCommand(
                key,
                null,
                "ana@example.com",
                mode,
                1_000,
                0,
                null,
                null,
                0,
                null,
                1_000,
                1,
                mode == FulfillmentMode.DELIVERY ? 3 : null,
                "pricing-v1",
                mapper.createObjectNode().put("label", "Ponto de demonstração — Belém"),
                List.of(new CreateOrderCommand.Item(UUID.randomUUID(), "Farinha", "500 g", 1, 1_000, 1_000)),
                UUID.randomUUID());
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM purchase_order WHERE id = ?", String.class, id);
    }
}
