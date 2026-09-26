package br.com.deladopara.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.pricing.domain.CouponRejection;
import br.com.deladopara.support.PostgresTestContainer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresTestContainer.class)
class CouponReservationServiceIT {

    private final CouponReservationService service;

    private final JdbcTemplate jdbc;

    @Autowired
    CouponReservationServiceIT(CouponReservationService service, JdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE coupon_usage, coupon CASCADE");
    }

    private void coupon(
            String code, Integer globalLimit, int perEmail, long minimum, boolean active, Instant from, Instant until) {
        jdbc.update(
                "INSERT INTO coupon (id, code_normalized, discount_type, discount_value, minimum_cents, valid_from,"
                        + " valid_until, active, global_limit, per_email_limit) VALUES (?, ?, 'PERCENTAGE', 10, ?, ?, ?,"
                        + " ?, ?, ?)",
                UUID.randomUUID(),
                code,
                minimum,
                Timestamp.from(from),
                Timestamp.from(until),
                active,
                globalLimit,
                perEmail);
    }

    private void openCoupon(String code, Integer globalLimit, int perEmail) {
        coupon(
                code,
                globalLimit,
                perEmail,
                0,
                true,
                Instant.now().minusSeconds(3600),
                Instant.now().plusSeconds(3600));
    }

    @Test
    void reservesWithNormalizedCodeAndEmailAndReplaysTheSameKeyIdempotently() {
        openCoupon("BEMVINDO", 10, 1);

        var first = service.reserve("  bemvindo ", "Ana@Example.com", 2_000, "order-1");
        var replay = service.reserve("BEMVINDO", "ana@example.com", 2_000, "order-1");

        assertThat(first.reserved()).isTrue();
        assertThat(first.replayed()).isFalse();
        assertThat(replay.reserved()).isTrue();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.usageId()).isEqualTo(first.usageId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coupon_usage", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rejectsWithStableReasons() {
        var now = Instant.now();
        coupon("OFF", null, 1, 0, false, now.minusSeconds(60), now.plusSeconds(60));
        coupon("FUTURO", null, 1, 0, true, now.plusSeconds(60), now.plusSeconds(120));
        coupon("VENCIDO", null, 1, 0, true, now.minusSeconds(120), now.minusSeconds(60));
        coupon("MIN", null, 1, 5_000, true, now.minusSeconds(60), now.plusSeconds(60));

        assertThat(service.reserve("nope", "a@x.com", 100, "k1").rejection()).isEqualTo(CouponRejection.NOT_FOUND);
        assertThat(service.reserve("off", "a@x.com", 100, "k2").rejection()).isEqualTo(CouponRejection.INACTIVE);
        assertThat(service.reserve("futuro", "a@x.com", 100, "k3").rejection())
                .isEqualTo(CouponRejection.NOT_YET_VALID);
        assertThat(service.reserve("vencido", "a@x.com", 100, "k4").rejection()).isEqualTo(CouponRejection.EXPIRED);
        assertThat(service.reserve("min", "a@x.com", 4_999, "k5").rejection()).isEqualTo(CouponRejection.BELOW_MINIMUM);
        assertThat(service.reserve("min", "a@x.com", 5_000, "k6").reserved()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM coupon_usage", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void perEmailLimitIsRestoredByReleaseAndByConfirmedFullRefundButGlobalLimitStaysSpentAfterRefund() {
        openCoupon("UNICO", 2, 1);

        assertThat(service.reserve("unico", "a@x.com", 100, "a1").reserved()).isTrue();
        assertThat(service.reserve("unico", "a@x.com", 100, "a2").rejection())
                .isEqualTo(CouponRejection.EMAIL_LIMIT_REACHED);

        service.release("a1");
        assertThat(service.reserve("unico", "a@x.com", 100, "a3").reserved()).isTrue();
        service.consume("a3");
        service.consume("a3");

        assertThat(service.reserve("unico", "b@x.com", 100, "b1").reserved()).isTrue();
        assertThat(service.reserve("unico", "c@x.com", 100, "c1").rejection())
                .isEqualTo(CouponRejection.GLOBAL_LIMIT_REACHED);

        service.markFullyRefunded("a3");
        assertThat(service.reserve("unico", "c@x.com", 100, "c2").rejection())
                .isEqualTo(CouponRejection.GLOBAL_LIMIT_REACHED);
        service.release("b1");
        assertThat(service.reserve("unico", "c@x.com", 100, "c3").reserved()).isTrue();
        assertThat(service.reserve("unico", "a@x.com", 100, "a4").rejection())
                .isEqualTo(CouponRejection.GLOBAL_LIMIT_REACHED);
    }

    @Test
    void invalidTransitionsAreRejected() {
        openCoupon("TRANS", null, 5);
        service.reserve("trans", "a@x.com", 100, "t1");
        service.consume("t1");

        assertThatThrownBy(() -> service.release("t1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.consume("missing")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.markFullyRefunded("t2")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concurrentReservationsNeverExceedTheGlobalLimit() throws Exception {
        openCoupon("CORRIDA", 3, 1);
        var executor = Executors.newFixedThreadPool(12);
        try {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (var i = 0; i < 12; i++) {
                var n = i;
                tasks.add(() -> service.reserve("corrida", "user" + n + "@x.com", 100, "race-" + n)
                        .reserved());
            }
            var granted = executor.invokeAll(tasks).stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .count();

            assertThat(granted).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM coupon_usage", Integer.class))
                    .isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentReservationsForTheSameEmailNeverExceedThePerEmailLimit() throws Exception {
        openCoupon("MESMO", null, 1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (var i = 0; i < 8; i++) {
                var n = i;
                tasks.add(() ->
                        service.reserve("mesmo", "same@x.com", 100, "same-" + n).reserved());
            }
            var granted = executor.invokeAll(tasks).stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .count();

            assertThat(granted).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCallsWithTheSameKeyReplayTheSingleReservation() throws Exception {
        openCoupon("REPETIDO", null, 5);
        var executor = Executors.newFixedThreadPool(6);
        try {
            var tasks = new ArrayList<Callable<UUID>>();
            for (var i = 0; i < 6; i++) {
                tasks.add(() ->
                        service.reserve("repetido", "a@x.com", 100, "same-key").usageId());
            }
            var usageIds = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .distinct()
                    .toList();

            assertThat(usageIds).hasSize(1).doesNotContainNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM coupon_usage", Integer.class))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
