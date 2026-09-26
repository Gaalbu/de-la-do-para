package br.com.deladopara.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.inventory.application.StockReservationService.InsufficientStockException;
import br.com.deladopara.inventory.application.StockReservationService.Line;
import br.com.deladopara.inventory.application.StockReservationService.Reservation;
import br.com.deladopara.inventory.application.StockReservationService.ReservationNotActiveException;
import br.com.deladopara.inventory.application.StockReservationService.Status;
import br.com.deladopara.support.PostgresTestContainer;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(PostgresTestContainer.class)
class StockReservationIT {

    private static final Instant NOW = Instant.parse("2026-09-24T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);
    private static final LocalDate ARRIVAL = TODAY.plusDays(5);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final MutableClock clock = new MutableClock(NOW);
    private final StockReservationService service;

    @Autowired
    StockReservationIT(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.service = new StockReservationService(jdbc, clock);
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE inventory_reservation_line, inventory_reservation, inventory_movements,"
                + " inventory_lots CASCADE");
        clock.set(NOW);
    }

    private UUID foodSku(int shelfLifeDays) {
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
                        + " updated_at) VALUES (?, ?, 'FOOD', ?, 'pacote', 500, ?, 200, 140, 50, 520, now(), now())",
                sku,
                product,
                "SKU-" + suffix.toUpperCase(),
                shelfLifeDays);
        return sku;
    }

    private UUID lot(UUID sku, int units, LocalDate expiresOn, int shelfLifeDays, Instant receivedAt, boolean blocked) {
        var id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO inventory_lots (id, sku_id, physical_units, blocked, expires_on, minimum_shelf_life_days,"
                        + " received_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())",
                id,
                sku,
                units,
                blocked,
                expiresOn,
                shelfLifeDays,
                Timestamp.from(receivedAt));
        return id;
    }

    private int reserved(UUID lot) {
        return jdbc.queryForObject("SELECT reserved_units FROM inventory_lots WHERE id = ?", Integer.class, lot);
    }

    private int movements(String type) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM inventory_movements WHERE movement_type = ?", Integer.class, type);
    }

    private Reservation reserve(String reference, List<Line> lines) {
        return tx.execute(status -> service.reserve(reference, ARRIVAL, lines));
    }

    @Test
    void allocatesFirstExpiryFirstAndSplitsAcrossLots() {
        var sku = foodSku(30);
        var later = lot(sku, 5, ARRIVAL.plusDays(90), 30, NOW.minusSeconds(7200), false);
        var sooner = lot(sku, 2, ARRIVAL.plusDays(40), 30, NOW.minusSeconds(60), false);

        var reservation = reserve("order-1", List.of(new Line(sku, 3)));

        assertThat(reservation.status()).isEqualTo(Status.ACTIVE);
        assertThat(reservation.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(reserved(sooner)).isEqualTo(2);
        assertThat(reserved(later)).isEqualTo(1);
        assertThat(movements("RESERVATION")).isEqualTo(1);
    }

    @Test
    void skipsBlockedExpiredAndTooShortLotsWithExactMarginAccepted() {
        var sku = foodSku(30);
        var blocked = lot(sku, 5, ARRIVAL.plusDays(60), 30, NOW, true);
        var expired = lot(sku, 5, TODAY.minusDays(1), 0, NOW, false);
        var oneDayShort = lot(sku, 5, ARRIVAL.plusDays(29), 30, NOW, false);
        var exactMargin = lot(sku, 1, ARRIVAL.plusDays(30), 30, NOW, false);

        reserve("order-2", List.of(new Line(sku, 1)));

        assertThat(reserved(exactMargin)).isEqualTo(1);
        assertThat(List.of(reserved(blocked), reserved(expired), reserved(oneDayShort)))
                .containsOnly(0);
        assertThatThrownBy(() -> reserve("order-3", List.of(new Line(sku, 1))))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void missingUnitOfAnySkuLeavesNoPartialReservation() {
        var plenty = foodSku(30);
        var scarce = foodSku(30);
        var plentyLot = lot(plenty, 10, ARRIVAL.plusDays(60), 30, NOW, false);
        lot(scarce, 1, ARRIVAL.plusDays(60), 30, NOW, false);

        assertThatThrownBy(() -> reserve("order-4", List.of(new Line(plenty, 2), new Line(scarce, 2))))
                .isInstanceOf(InsufficientStockException.class)
                .extracting(e -> ((InsufficientStockException) e).skuId())
                .isEqualTo(scarce);

        assertThat(reserved(plentyLot)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservation", Integer.class))
                .isZero();
        assertThat(movements("RESERVATION")).isZero();
    }

    @Test
    void twoBuyersForTheLastUnitGetExactlyOneReservation() throws Exception {
        var sku = foodSku(30);
        var last = lot(sku, 1, ARRIVAL.plusDays(60), 30, NOW, false);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        var outcomes = new ArrayList<String>();
        try {
            var calls = new ArrayList<Callable<Reservation>>();
            for (var reference : List.of("buyer-a", "buyer-b")) {
                calls.add(() -> {
                    start.await();
                    return reserve(reference, List.of(new Line(sku, 1)));
                });
            }
            var futures = calls.stream().map(pool::submit).toList();
            start.countDown();
            for (var future : futures) {
                try {
                    future.get();
                    outcomes.add("reserved");
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(InsufficientStockException.class);
                    outcomes.add("unavailable");
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(outcomes).containsExactlyInAnyOrder("reserved", "unavailable");
        assertThat(reserved(last)).isEqualTo(1);
    }

    @Test
    void replayCommitAndReleaseNeverRepeatMovements() {
        var sku = foodSku(30);
        var lot = lot(sku, 4, ARRIVAL.plusDays(60), 30, NOW, false);
        var first = reserve("order-5", List.of(new Line(sku, 2)));

        assertThat(reserve("order-5", List.of(new Line(sku, 2))).id()).isEqualTo(first.id());
        tx.executeWithoutResult(s -> service.release("order-5"));
        tx.executeWithoutResult(s -> service.release("order-5"));

        assertThat(reserved(lot)).isZero();
        assertThat(movements("RESERVATION")).isEqualTo(1);
        assertThat(movements("RELEASE")).isEqualTo(1);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> service.commit("order-5")))
                .isInstanceOf(ReservationNotActiveException.class);

        reserve("order-6", List.of(new Line(sku, 1)));
        tx.executeWithoutResult(s -> service.commit("order-6"));
        tx.executeWithoutResult(s -> service.commit("order-6"));
        tx.executeWithoutResult(s -> service.release("order-6"));
        assertThat(service.find("order-6").orElseThrow().status()).isEqualTo(Status.COMMITTED);
        assertThat(reserved(lot)).isEqualTo(1);
    }

    @Test
    void commitIsRejectedAtTheExactExpiryInstant() {
        var sku = foodSku(30);
        lot(sku, 2, ARRIVAL.plusDays(60), 30, NOW, false);
        reserve("order-7", List.of(new Line(sku, 1)));
        reserve("order-8", List.of(new Line(sku, 1)));

        clock.set(NOW.plus(Duration.ofMinutes(15)).minusMillis(1));
        tx.executeWithoutResult(s -> service.commit("order-7"));
        clock.set(NOW.plus(Duration.ofMinutes(15)));

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> service.commit("order-8")))
                .isInstanceOf(ReservationNotActiveException.class);
    }

    @Test
    void blockingALotKeepsItsReservationsAndPhysicalCannotDropBelowThem() {
        var sku = foodSku(30);
        var lot = lot(sku, 3, ARRIVAL.plusDays(60), 30, NOW, false);
        reserve("order-9", List.of(new Line(sku, 2)));

        jdbc.update("UPDATE inventory_lots SET blocked = true WHERE id = ?", lot);

        assertThat(reserved(lot)).isEqualTo(2);
        assertThat(service.find("order-9").orElseThrow().status()).isEqualTo(Status.ACTIVE);
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory_lots SET physical_units = 1 WHERE id = ?", lot))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private static final class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
