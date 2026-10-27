package br.com.deladopara.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.eventing.application.EventConsumptionOutcome;
import br.com.deladopara.eventing.application.EventConsumptionService;
import br.com.deladopara.eventing.application.EventEnvelope;
import br.com.deladopara.inventory.application.StockReservationService;
import br.com.deladopara.orders.application.CreateOrderCommand;
import br.com.deladopara.orders.application.OrderService;
import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.payments.adapter.simulated.SimulatedPaymentProvider;
import br.com.deladopara.payments.application.CheckoutOperationRunner;
import br.com.deladopara.payments.application.CheckoutOperations;
import br.com.deladopara.payments.application.PaymentIntentService;
import br.com.deladopara.payments.application.ProviderEventInbox;
import br.com.deladopara.payments.application.ProviderEventProcessor;
import br.com.deladopara.pricing.application.CouponReservationService;
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

/** C66: unpaid purchases expire at the end of the hold, once, without losing a payment that arrives late. */
@SpringBootTest
@Import(PostgresTestContainer.class)
class CheckoutExpirationIT {

    private static final long TOTAL = 3_600;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final OrderService orders;
    private final StockReservationService stock;
    private final CouponReservationService coupons;
    private final PaymentIntentService intents;
    private final CheckoutOperations operations;
    private final ProviderEventInbox inbox;
    private final EventConsumptionService consumption;
    private final SimulatedPaymentProvider simulator = new SimulatedPaymentProvider(Clock.systemUTC());
    private final ProviderEventProcessor processor;
    private final CheckoutExpirationService expirations;

    @Autowired
    CheckoutExpirationIT(
            JdbcTemplate jdbc,
            TransactionTemplate tx,
            ObjectMapper objectMapper,
            OrderService orders,
            StockReservationService stock,
            CouponReservationService coupons,
            PaymentIntentService intents,
            CheckoutOperations operations,
            ProviderEventInbox inbox,
            EventConsumptionService consumption,
            CheckoutExpirationService expirations) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.orders = orders;
        this.stock = stock;
        this.coupons = coupons;
        this.intents = intents;
        this.operations = operations;
        this.inbox = inbox;
        this.consumption = consumption;
        this.expirations = expirations;
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
        jdbc.execute("TRUNCATE payment_provider_event, payment_external_operation, payment_intent,"
                + " purchase_order_status_history, purchase_order_item, purchase_order, inventory_reservation_line,"
                + " inventory_reservation, inventory_movements, inventory_lots, event_consumption,"
                + " event_consumer_cursor, event_outbox CASCADE");
        for (var t : triggers) {
            jdbc.execute("ALTER TABLE " + t[0] + " ENABLE TRIGGER " + t[1]);
        }
    }

    /** A pending order with 2 reserved units and a hosted checkout awaiting payment. */
    private Purchase pendingPurchase() {
        return pendingPurchase(false);
    }

    private Purchase pendingPurchase(boolean withCoupon) {
        var sku = sku();
        var couponCode = withCoupon
                ? "EXPIRA" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                : null;
        if (withCoupon) {
            jdbc.update(
                    "INSERT INTO coupon (id, code_normalized, discount_type, discount_value, minimum_cents,"
                            + " valid_from, valid_until, active, global_limit, per_email_limit)"
                            + " VALUES (?, ?, 'FIXED', 100, 0, now() - interval '1 day',"
                            + " now() + interval '1 day', true, 10, 1)",
                    UUID.randomUUID(),
                    couponCode);
        }
        var discount = withCoupon ? 100L : 0L;
        var orderId = orders.create(new CreateOrderCommand(
                        UUID.randomUUID().toString(),
                        null,
                        "ana@example.com",
                        FulfillmentMode.PICKUP,
                        TOTAL,
                        0,
                        withCoupon ? "FIXED" : null,
                        withCoupon ? 100L : null,
                        discount,
                        couponCode,
                        TOTAL - discount,
                        1,
                        null,
                        "pricing-v1",
                        objectMapper.createObjectNode().put("label", "Ponto de demonstração — Belém"),
                        List.of(new CreateOrderCommand.Item(sku, "Farinha", "pacote", 2, 1_800, TOTAL)),
                        UUID.randomUUID()))
                .id();
        tx.executeWithoutResult(s -> stock.reserve(
                "order:" + orderId, LocalDate.now().plusDays(1), List.of(new StockReservationService.Line(sku, 2))));
        var intentId = intents.request(orderId, TOTAL - discount, UUID.randomUUID());
        if (withCoupon) {
            tx.executeWithoutResult(
                    s -> assertThat(coupons.reserve(couponCode, "ana@example.com", TOTAL, "order:" + orderId)
                                    .reserved())
                            .isTrue());
        }
        new CheckoutOperationRunner(operations, simulator).runNext();
        var checkoutId = jdbc.queryForObject(
                "SELECT provider_checkout_id FROM payment_intent WHERE id = ?", String.class, intentId);
        return new Purchase(orderId, intentId, checkoutId, sku);
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

    private void webhookPaid(Purchase purchase, String eventId) {
        inbox.record(
                "ASAAS",
                new ProviderEventInbox.Notification(
                        eventId, "CHECKOUT_PAID", purchase.checkoutId(), "PAID", "2026-09-25 10:00:00"));
    }

    /** Feeds every payment event of the intent to the consumer, in version order, as Kafka would. */
    private List<EventConsumptionOutcome> deliverPaymentEvents(UUID intentId) {
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
                intentId.toString());
        var outcomes = new ArrayList<EventConsumptionOutcome>();
        for (var envelope : envelopes) {
            outcomes.add(consumption.consume(envelope));
        }
        return outcomes;
    }

    private String orderStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM purchase_order WHERE id = ?", String.class, orderId);
    }

    private String intentStatus(UUID intentId) {
        return jdbc.queryForObject("SELECT status FROM payment_intent WHERE id = ?", String.class, intentId);
    }

    private String reservationStatus(UUID orderId) {
        return jdbc.queryForObject(
                "SELECT status FROM inventory_reservation WHERE reference = ?", String.class, "order:" + orderId);
    }

    private int reservedUnits(UUID sku) {
        return jdbc.queryForObject("SELECT reserved_units FROM inventory_lots WHERE sku_id = ?", Integer.class, sku);
    }

    /** Moves the purchase's 15-minute hold into the past, keeping its length. */
    private void holdRanOut(Purchase purchase) {
        jdbc.update(
                "UPDATE inventory_reservation SET created_at = now() - interval '20 minutes',"
                        + " expires_at = now() - interval '5 minutes' WHERE reference = ?",
                "order:" + purchase.orderId());
    }

    private String couponState(Purchase purchase) {
        return jdbc.queryForObject(
                "SELECT state FROM coupon_usage WHERE reservation_key = ?",
                String.class,
                "order:" + purchase.orderId());
    }

    private int releaseMovements(Purchase purchase) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM inventory_movements m JOIN inventory_reservation r"
                        + " ON m.idempotency_key LIKE 'release:' || r.id || ':%' WHERE r.reference = ?",
                Integer.class, "order:" + purchase.orderId());
    }

    @Test
    void dueOrderExpiresAndReleasesStockAndCouponOnce() {
        var purchase = pendingPurchase(true);
        holdRanOut(purchase);

        assertThat(expirations.dueOrders(10)).containsExactly(purchase.orderId());
        assertThat(expirations.expire(purchase.orderId())).isTrue();
        assertThat(expirations.expire(purchase.orderId())).isFalse();

        assertThat(orderStatus(purchase.orderId())).isEqualTo("EXPIRED");
        assertThat(reservationStatus(purchase.orderId())).isEqualTo("RELEASED");
        assertThat(reservedUnits(purchase.sku())).isZero();
        assertThat(couponState(purchase)).isEqualTo("RELEASED");
        assertThat(releaseMovements(purchase)).isEqualTo(1);
        assertThat(expirations.dueOrders(10)).isEmpty();
    }

    @Test
    void holdStillRunningIsKept() {
        var purchase = pendingPurchase();

        assertThat(expirations.dueOrders(10)).isEmpty();
        assertThat(expirations.expire(purchase.orderId())).isFalse();

        assertThat(orderStatus(purchase.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(reservationStatus(purchase.orderId())).isEqualTo("ACTIVE");
    }

    @Test
    void confirmationInTransitIsLeftToTheOutcomeHandler() {
        var purchase = pendingPurchase();
        simulator.pay(purchase.checkoutId(), TOTAL);
        webhookPaid(purchase, "evt_in_transit");
        processor.processNext();
        holdRanOut(purchase);

        assertThat(expirations.expire(purchase.orderId())).isFalse();
        deliverPaymentEvents(purchase.intentId());

        assertThat(orderStatus(purchase.orderId())).isEqualTo("UNDER_REVIEW");
        assertThat(reservationStatus(purchase.orderId())).isEqualTo("RELEASED");
        assertThat(intentStatus(purchase.intentId())).isEqualTo("REFUND_REQUESTED");
    }

    @Test
    void paymentAfterExpiryKeepsTheOrderExpiredAndRequestsTheRefund() {
        var purchase = pendingPurchase();
        holdRanOut(purchase);
        assertThat(expirations.expire(purchase.orderId())).isTrue();

        simulator.pay(purchase.checkoutId(), TOTAL);
        webhookPaid(purchase, "evt_after_expiry");
        processor.processNext();
        deliverPaymentEvents(purchase.intentId());

        assertThat(orderStatus(purchase.orderId())).isEqualTo("EXPIRED");
        assertThat(intentStatus(purchase.intentId())).isEqualTo("REFUND_REQUESTED");
        assertThat(releaseMovements(purchase)).isEqualTo(1);
    }

    @Test
    void concurrentExpiryAndConfirmationEndInOneValidState() throws Exception {
        for (int round = 0; round < 5; round++) {
            var purchase = pendingPurchase();
            simulator.pay(purchase.checkoutId(), TOTAL);
            webhookPaid(purchase, "evt_race_" + round);
            holdRanOut(purchase);

            race(() -> processor.processNext(), () -> expirations.expire(purchase.orderId()));
            deliverPaymentEvents(purchase.intentId());

            assertThat(orderStatus(purchase.orderId())).isIn("EXPIRED", "UNDER_REVIEW");
            assertThat(reservationStatus(purchase.orderId())).isEqualTo("RELEASED");
            assertThat(intentStatus(purchase.intentId())).isEqualTo("REFUND_REQUESTED");
            assertThat(releaseMovements(purchase)).isEqualTo(1);
            assertThat(reservedUnits(purchase.sku())).isZero();
        }
    }

    @Test
    void concurrentExpirersExpireTheOrderOnce() throws Exception {
        var purchase = pendingPurchase(true);
        holdRanOut(purchase);

        var results = race(
                () -> expirations.expire(purchase.orderId()),
                () -> expirations.expire(purchase.orderId()),
                () -> expirations.expire(purchase.orderId()));

        assertThat(results).containsOnlyOnce(true);
        assertThat(releaseMovements(purchase)).isEqualTo(1);
        assertThat(couponState(purchase)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM purchase_order_status_history WHERE order_id = ? AND to_status = 'EXPIRED'",
                        Integer.class,
                        purchase.orderId()))
                .isEqualTo(1);
    }

    /** Starts every task at the same moment and returns their results in order. */
    @SafeVarargs
    private List<Object> race(Callable<?>... tasks) throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(tasks.length);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (var task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
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
