package br.com.deladopara.pricing.adapter.persistence;

import br.com.deladopara.pricing.domain.CouponDiscount;
import br.com.deladopara.pricing.domain.CouponUsageState;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Coupon rows and usage ledger. All mutations require a surrounding transaction. */
@Repository
public class CouponUsageRepository {

    private final JdbcTemplate jdbc;

    public CouponUsageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Locks the coupon row, serialising concurrent reservations of the same coupon. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CouponRow> lockByCode(String codeNormalized) {
        return jdbc
                .query(
                        "SELECT * FROM coupon WHERE code_normalized = ? FOR UPDATE",
                        (rs, row) -> new CouponRow(
                                rs.getObject("id", UUID.class),
                                new CouponDiscount(
                                        CouponDiscount.Type.valueOf(rs.getString("discount_type")),
                                        rs.getLong("discount_value"),
                                        rs.getLong("minimum_cents")),
                                rs.getTimestamp("valid_from").toInstant(),
                                rs.getTimestamp("valid_until").toInstant(),
                                rs.getBoolean("active"),
                                (Integer) rs.getObject("global_limit"),
                                rs.getInt("per_email_limit")),
                        codeNormalized)
                .stream()
                .findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UsageRow> lockUsage(String reservationKey) {
        return jdbc
                .query(
                        "SELECT u.id, u.state, u.coupon_id, c.discount_type, c.discount_value, c.minimum_cents"
                                + " FROM coupon_usage u JOIN coupon c ON c.id = u.coupon_id"
                                + " WHERE u.reservation_key = ? FOR UPDATE OF u",
                        (rs, row) -> new UsageRow(
                                rs.getObject("id", UUID.class),
                                rs.getObject("coupon_id", UUID.class),
                                CouponUsageState.valueOf(rs.getString("state")),
                                new CouponDiscount(
                                        CouponDiscount.Type.valueOf(rs.getString("discount_type")),
                                        rs.getLong("discount_value"),
                                        rs.getLong("minimum_cents"))),
                        reservationKey)
                .stream()
                .findFirst();
    }

    /** Global limit is historical: RESERVED, CONSUMED and REFUNDED all count; only RELEASED frees capacity. */
    public int globalUsage(UUID couponId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM coupon_usage WHERE coupon_id = ? AND state <> 'RELEASED'",
                Integer.class,
                couponId);
    }

    /** Per-email limit (D34): a fully refunded usage restores eligibility, as does a released reservation. */
    public int emailUsage(UUID couponId, String emailNormalized) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM coupon_usage WHERE coupon_id = ? AND email_normalized = ?"
                        + " AND state IN ('RESERVED', 'CONSUMED')",
                Integer.class,
                couponId,
                emailNormalized);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID insertReservation(UUID couponId, String emailNormalized, String reservationKey, Instant now) {
        var id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO coupon_usage (id, coupon_id, email_normalized, reservation_key, state, created_at,"
                        + " updated_at) VALUES (?, ?, ?, ?, 'RESERVED', ?, ?)",
                id,
                couponId,
                emailNormalized,
                reservationKey,
                Timestamp.from(now),
                Timestamp.from(now));
        return id;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void transition(UUID usageId, CouponUsageState to, Instant now) {
        jdbc.update(
                "UPDATE coupon_usage SET state = ?, updated_at = ? WHERE id = ?",
                to.name(),
                Timestamp.from(now),
                usageId);
    }

    public record CouponRow(
            UUID id,
            CouponDiscount discount,
            Instant validFrom,
            Instant validUntil,
            boolean active,
            Integer globalLimit,
            int perEmailLimit) {}

    public record UsageRow(UUID id, UUID couponId, CouponUsageState state, CouponDiscount discount) {}
}
