package br.com.deladopara.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.checkout.application.CheckoutCancellationService.OrderNotCancellableException;
import br.com.deladopara.eventing.application.EventConsumptionService;
import br.com.deladopara.eventing.application.EventEnvelope;
import br.com.deladopara.inventory.application.StockReservationService;
import br.com.deladopara.orders.application.CreateOrderCommand;
import br.com.deladopara.orders.application.OrderService;
import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import br.com.deladopara.payments.adapter.simulated.SimulatedPaymentProvider;
import br.com.deladopara.payments.application.CheckoutOperationRunner;
import br.com.deladopara.payments.application.CheckoutOperations;
import br.com.deladopara.payments.application.PaymentIntentService;
import br.com.deladopara.payments.application.ProviderEventInbox;
import br.com.deladopara.payments.application.ProviderEventProcessor;
import br.com.deladopara.payments.application.RefundOperations;
import br.com.deladopara.payments.application.RefundRunner;
import br.com.deladopara.pricing.application.CouponReservationService;
import br.com.deladopara.shipping.application.PickupService;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** C68: cancellation respects the dispatch/pickup boundary, returns stock once and refunds only through payments. */
@SpringBootTest
@Import(PostgresTestContainer.class)
class OrderCancellationIT {

    private static final long TOTAL = 3_600;
    private static final long DISCOUNT = 100;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final OrderService orders;
    private final StockReservationService stock;
    private final CouponReservationService coupons;
    private final PaymentIntentService intents;
    private final CheckoutOperations operations;
    private final RefundOperations refunds;
    private final ProviderEventInbox inbox;
    private final EventConsumptionService consumption;
    private final PickupService pickups;
    private final CheckoutCancellationService cancellations;
    private final SimulatedPaymentProvider simulator = new SimulatedPaymentProvider(Clock.systemUTC());
    private final ProviderEventProcessor processor;

    @Autowired
    OrderCancellationIT(
            JdbcTemplate jdbc,
            TransactionTemplate tx,
            ObjectMapper objectMapper,
            OrderService orders,
            StockReservationService stock,
            CouponReservationService coupons,
            PaymentIntentService intents,
            CheckoutOperations operations,
            RefundOperations refunds,
            ProviderEventInbox inbox,
            EventConsumptionService consumption,
            PickupService pickups,
            CheckoutCancellationService cancellations) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.orders = orders;
        this.stock = stock;
        this.coupons = coupons;
        this.intents = intents;
        this.operations = operations;
        this.refunds = refunds;
        this.inbox = inbox;
        this.consumption = consumption;
        this.pickups = pickups;
        this.cancellations = cancellations;
        this.processor = new ProviderEventProcessor(jdbc, tx, intents, simulator, Clock.systemUTC());
    }

    @BeforeEach
    void clean() {
        String[][] triggers = {
            {"purchase_order_item", "purchase_order_item_immutable"},
            {"purchase_order_status_history", "purchase_order_history_immutable"},
            {"purchase_order", "purchase_order_snapshot_guard"},
            {"payment_intent", "payment_intent_reference_guard"}
        };
        for (var t : triggers) {
            jdbc.execute("ALTER TABLE " + t[0] + " DISABLE TRIGGER " + t[1]);
        }
        jdbc.execute(
                "TRUNCATE payment_provider_event, payment_external_operation, payment_intent, shipping_pickup_code,"
                        + " purchase_order_status_history, purchase_order_item, purchase_order, inventory_reservation_line,"
                        + " inventory_reservation, inventory_movements, inventory_lots, event_consumption,"
                        + " event_consumer_cursor, event_outbox CASCADE");
        for (var t : triggers) {
            jdbc.execute("ALTER TABLE " + t[0] + " ENABLE TRIGGER " + t[1]);
        }
    }

    /** A pending order with 2 reserved units, a 1,00 coupon and a hosted checkout awaiting payment. */
    private Purchase pendingPurchase(FulfillmentMode mode) {
        var sku = sku();
        var couponCode =
                "CANCELA" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.update(
                "INSERT INTO coupon (id, code_normalized, discount_type, discount_value, minimum_cents,"
                        + " valid_from, valid_until, active, global_limit, per_email_limit)"
                        + " VALUES (?, ?, 'FIXED', ?, 0, now() - interval '1 day', now() + interval '1 day', true,"
                        + " 10, 1)",
                UUID.randomUUID(),
                couponCode,
                DISCOUNT);
        var destination = objectMapper
                .createObjectNode()
                .put("label", "Ponto de demonstração — Belém")
                .put("window", "seg–sex, 9h–17h");
        var orderId = orders.create(new CreateOrderCommand(
                        UUID.randomUUID().toString(),
                        null,
                        "ana@example.com",
                        mode,
                        TOTAL,
                        0,
                        "FIXED",
                        DISCOUNT,
                        DISCOUNT,
                        couponCode,
                        TOTAL - DISCOUNT,
                        1,
                        mode == FulfillmentMode.DELIVERY ? 3 : null,
                        "pricing-v1",
                        destination,
                        List.of(new CreateOrderCommand.Item(sku, "Farinha", "pacote", 2, 1_800, TOTAL)),
                        UUID.randomUUID()))
                .id();
        var reference = "order:" + orderId;
        tx.executeWithoutResult(s -> {
            stock.reserve(reference, LocalDate.now().plusDays(1), List.of(new StockReservationService.Line(sku, 2)));
            assertThat(coupons.reserve(couponCode, "ana@example.com", TOTAL, reference)
                            .reserved())
                    .isTrue();
        });
        var intentId = intents.request(orderId, TOTAL - DISCOUNT, UUID.randomUUID());
        new CheckoutOperationRunner(operations, simulator).runNext();
        var checkoutId = jdbc.queryForObject(
                "SELECT provider_checkout_id FROM payment_intent WHERE id = ?", String.class, intentId);
        return new Purchase(orderId, intentId, checkoutId, sku);
    }

    private Purchase paidPurchase(FulfillmentMode mode) {
        var purchase = pendingPurchase(mode);
        pay(purchase);
        assertThat(orderStatus(purchase)).isEqualTo("PAID");
        return purchase;
    }

    /** The buyer pays, the webhook arrives, the provider confirms it and the checkout consumes the events. */
    private void pay(Purchase purchase) {
        simulator.pay(purchase.checkoutId(), TOTAL - DISCOUNT);
        inbox.record(
                "ASAAS",
                new ProviderEventInbox.Notification(
                        "evt_" + purchase.orderId(),
                        "CHECKOUT_PAID",
                        purchase.checkoutId(),
                        "PAID",
                        "2026-10-05 10:00:00"));
        processor.processNext();
        deliverPaymentEvents(purchase);
    }

    private UUID sku() {
        var producer = UUID.randomUUID();
        var product = UUID.randomUUID();
        var sku = UUID.randomUUID();
        var suffix = sku.toString().substring(0, 8);
        jdbc.update(
                "INSERT INTO producers (id, slug, display_name, origin_label, description, created_at, updated_at)"
                        + " VALUES (?, ?, 'Produtor', 'Belém/PA', 'Demonstração', now(), now())",
                producer,
                "p-" + suffix);
        jdbc.update(
                "INSERT INTO products (id, slug, display_name, description, category, producer_id, created_at,"
                        + " updated_at) VALUES (?, ?, 'Farinha', 'Demonstração', 'FOOD', ?, now(), now())",
                product,
                "farinha-" + suffix,
                producer);
        jdbc.update(
                "INSERT INTO product_skus (id, product_id, product_category, sku_code, sales_unit, net_content_grams,"
                        + " minimum_shelf_life_days, length_mm, width_mm, height_mm, gross_weight_grams, created_at,"
                        + " updated_at) VALUES (?, ?, 'FOOD', ?, 'pacote', 500, 30, 200, 140, 50, 520, now(), now())",
                sku,
                product,
                "SKU-" + suffix.toUpperCase());
        jdbc.update(
                "INSERT INTO inventory_lots (id, sku_id, physical_units, expires_on, minimum_shelf_life_days,"
                        + " received_at, created_at, updated_at) VALUES (?, ?, 5, ?, 30, ?, now(), now())",
                UUID.randomUUID(),
                sku,
                LocalDate.now().plusDays(180),
                Timestamp.from(Instant.now()));
        return sku;
    }

    /** Feeds every not yet consumed payment event of the intent to the consumer, in version order. */
    private void deliverPaymentEvents(Purchase purchase) {
        var envelopes = jdbc.query(
                "SELECT * FROM event_outbox WHERE aggregate_id = ? ORDER BY aggregate_version",
                (rs, row) -> {
                    try {
                        return new EventEnvelope(
                                rs.getObject("event_id", UUID.class),
                                rs.getString("event_type"),
                                rs.getInt("schema_version"),
                                rs.getString("aggregate_id"),
                                rs.getLong("aggregate_version"),
                                rs.getTimestamp("occurred_at").toInstant().toString(),
                                rs.getObject("correlation_id", UUID.class),
                                rs.getObject("causation_id", UUID.class),
                                objectMapper.readTree(rs.getString("payload")));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                },
                purchase.intentId().toString());
        envelopes.forEach(consumption::consume);
    }

    private String orderStatus(Purchase purchase) {
        return jdbc.queryForObject("SELECT status FROM purchase_order WHERE id = ?", String.class, purchase.orderId());
    }

    private String intentStatus(Purchase purchase) {
        return jdbc.queryForObject("SELECT status FROM payment_intent WHERE id = ?", String.class, purchase.intentId());
    }

    private String reservationStatus(Purchase purchase) {
        return jdbc.queryForObject(
                "SELECT status FROM inventory_reservation WHERE reference = ?",
                String.class,
                "order:" + purchase.orderId());
    }

    private String couponState(Purchase purchase) {
        return jdbc.queryForObject(
                "SELECT state FROM coupon_usage WHERE reservation_key = ?",
                String.class,
                "order:" + purchase.orderId());
    }

    private int reservedUnits(Purchase purchase) {
        return jdbc.queryForObject(
                "SELECT reserved_units FROM inventory_lots WHERE sku_id = ?", Integer.class, purchase.sku());
    }

    private int refundOperations(Purchase purchase) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM payment_external_operation WHERE intent_id = ? AND kind = 'REFUND'",
                Integer.class,
                purchase.intentId());
    }

    private int transitionsTo(Purchase purchase, String status) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM purchase_order_status_history WHERE order_id = ? AND to_status = ?",
                Integer.class,
                purchase.orderId(),
                status);
    }

    @Test
    void unpaidCancellationReleasesStockAndCouponWithoutTouchingThePayment() {
        var purchase = pendingPurchase(FulfillmentMode.PICKUP);

        var status = cancellations.cancel(purchase.orderId(), OrderActor.CUSTOMER, UUID.randomUUID());

        assertThat(status).isEqualTo(OrderStatus.CANCELLED);
        assertThat(reservationStatus(purchase)).isEqualTo("RELEASED");
        assertThat(reservedUnits(purchase)).isZero();
        assertThat(couponState(purchase)).isEqualTo("RELEASED");
        assertThat(intentStatus(purchase)).isEqualTo("AWAITING_PAYMENT");
        assertThat(refundOperations(purchase)).isZero();
    }

    @Test
    void paymentAfterAnUnpaidCancellationIsRefundedWithoutTakingStockAgain() {
        var purchase = pendingPurchase(FulfillmentMode.PICKUP);
        cancellations.cancel(purchase.orderId(), OrderActor.CUSTOMER, UUID.randomUUID());

        pay(purchase);

        assertThat(orderStatus(purchase)).isEqualTo("CANCELLED");
        assertThat(intentStatus(purchase)).isEqualTo("REFUND_REQUESTED");
        assertThat(reservedUnits(purchase)).isZero();
        assertThat(reservationStatus(purchase)).isEqualTo("RELEASED");
    }

    @Test
    void paidCancellationReturnsStockAndRefundsThenGivesTheCouponUseBack() {
        var purchase = paidPurchase(FulfillmentMode.PICKUP);
        assertThat(reservedUnits(purchase)).isEqualTo(2);

        cancellations.cancel(purchase.orderId(), OrderActor.CUSTOMER, UUID.randomUUID());

        assertThat(orderStatus(purchase)).isEqualTo("CANCELLED");
        assertThat(reservationStatus(purchase)).isEqualTo("RELEASED");
        assertThat(reservedUnits(purchase)).isZero();
        assertThat(couponState(purchase))
                .as("D34: kept until the refund settles")
                .isEqualTo("CONSUMED");
        assertThat(intentStatus(purchase)).isEqualTo("REFUND_REQUESTED");

        new RefundRunner(refunds, simulator).runNext();
        deliverPaymentEvents(purchase);

        assertThat(intentStatus(purchase)).isEqualTo("REFUNDED");
        assertThat(couponState(purchase)).isEqualTo("REFUNDED");
        assertThat(orderStatus(purchase)).isEqualTo("CANCELLED");
    }

    @Test
    void readyOrderCanStillBeCancelledButAPickedUpOneCannot() {
        var ready = paidPurchase(FulfillmentMode.PICKUP);
        pickups.startPreparation(ready.orderId(), UUID.randomUUID());
        pickups.markReady(ready.orderId(), UUID.randomUUID());

        assertThat(cancellations.cancel(ready.orderId(), OrderActor.CUSTOMER, UUID.randomUUID()))
                .isEqualTo(OrderStatus.CANCELLED);

        var collected = paidPurchase(FulfillmentMode.PICKUP);
        pickups.startPreparation(collected.orderId(), UUID.randomUUID());
        pickups.markReady(collected.orderId(), UUID.randomUUID());
        pickups.confirm(
                collected.orderId(), pickups.pickupInfo(collected.orderId()).code(), UUID.randomUUID());

        assertThatThrownBy(() -> cancellations.cancel(collected.orderId(), OrderActor.CUSTOMER, UUID.randomUUID()))
                .isInstanceOf(OrderNotCancellableException.class);
        assertThat(intentStatus(collected)).isEqualTo("CONFIRMED");
        assertThat(reservedUnits(collected)).isEqualTo(2);
    }

    @Test
    void cancellationAndPickupConfirmationAreMutuallyExclusive() throws Exception {
        var purchase = paidPurchase(FulfillmentMode.PICKUP);
        pickups.startPreparation(purchase.orderId(), UUID.randomUUID());
        pickups.markReady(purchase.orderId(), UUID.randomUUID());
        var code = pickups.pickupInfo(purchase.orderId()).code();

        var results =
                race(() -> cancellations.cancel(purchase.orderId(), OrderActor.CUSTOMER, UUID.randomUUID()), () -> {
                    pickups.confirm(purchase.orderId(), code, UUID.randomUUID());
                    return OrderStatus.PICKED_UP;
                });

        var cancelled = transitionsTo(purchase, "CANCELLED");
        var pickedUp = transitionsTo(purchase, "PICKED_UP");
        assertThat(cancelled + pickedUp).isEqualTo(1);
        assertThat(results).filteredOn(Throwable.class::isInstance).hasSize(1);
        if (cancelled == 1) {
            assertThat(intentStatus(purchase)).isEqualTo("REFUND_REQUESTED");
            assertThat(reservedUnits(purchase)).isZero();
        } else {
            assertThat(intentStatus(purchase)).isEqualTo("CONFIRMED");
            assertThat(reservedUnits(purchase)).isEqualTo(2);
        }
    }

    @Test
    void orderWithTheCarrierGoesToReviewWithoutAutomaticRefund() {
        var purchase = paidPurchase(FulfillmentMode.DELIVERY);
        orders.transition(purchase.orderId(), OrderStatus.PREPARING, OrderActor.ADMIN, null, UUID.randomUUID());
        orders.transition(
                purchase.orderId(), OrderStatus.IN_TRANSIT, OrderActor.SYSTEM, "HANDED_OFF", UUID.randomUUID());

        var status = cancellations.cancel(purchase.orderId(), OrderActor.CUSTOMER, UUID.randomUUID());

        assertThat(status).isEqualTo(OrderStatus.UNDER_REVIEW);
        assertThat(intentStatus(purchase)).isEqualTo("CONFIRMED");
        assertThat(refundOperations(purchase)).isZero();
        assertThat(reservedUnits(purchase)).isEqualTo(2);
    }

    @Test
    void repeatedAndConcurrentCancellationsActOnce() throws Exception {
        var purchase = paidPurchase(FulfillmentMode.PICKUP);

        var results = race(
                () -> cancellations.cancel(purchase.orderId(), OrderActor.CUSTOMER, UUID.randomUUID()),
                () -> cancellations.cancel(purchase.orderId(), OrderActor.CUSTOMER, UUID.randomUUID()),
                () -> cancellations.cancel(purchase.orderId(), OrderActor.ADMIN, UUID.randomUUID()));
        var again = cancellations.cancel(purchase.orderId(), OrderActor.CUSTOMER, UUID.randomUUID());

        assertThat(results).containsOnly(OrderStatus.CANCELLED);
        assertThat(again).isEqualTo(OrderStatus.CANCELLED);
        assertThat(transitionsTo(purchase, "CANCELLED")).isEqualTo(1);
        assertThat(refundOperations(purchase)).isEqualTo(1);
        assertThat(reservedUnits(purchase)).isZero();
    }

    /** Starts every task at the same moment; a task that throws yields its exception as the result. */
    @SafeVarargs
    private List<Object> race(Callable<?>... tasks) throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(tasks.length);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Object>>();
            for (var task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return task.call();
                    } catch (RuntimeException failure) {
                        return failure;
                    }
                }));
            }
            start.countDown();
            var results = new ArrayList<Object>();
            for (var future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private record Purchase(UUID orderId, UUID intentId, String checkoutId, UUID sku) {}
}
