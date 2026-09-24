package br.com.deladopara.pricing.adapter.persistence;

import br.com.deladopara.pricing.adapter.web.dto.CouponResponse;
import br.com.deladopara.pricing.domain.CouponDiscount;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Administrative coupon CRUD. Usage rows are never touched here, so queries cannot change usage. */
@Repository
public class CouponAdminRepository {

    private static final String SELECT = """
            SELECT c.*, (SELECT count(*) FROM coupon_usage u
                          WHERE u.coupon_id = c.id AND u.state <> 'RELEASED') AS global_usage
              FROM coupon c
            """;

    private static final RowMapper<CouponResponse> MAPPER = (rs, row) -> new CouponResponse(
            rs.getObject("id", UUID.class),
            rs.getString("code_normalized"),
            CouponDiscount.Type.valueOf(rs.getString("discount_type")),
            rs.getLong("discount_value"),
            rs.getLong("minimum_cents"),
            rs.getTimestamp("valid_from").toInstant(),
            rs.getTimestamp("valid_until").toInstant(),
            rs.getBoolean("active"),
            (Integer) rs.getObject("global_limit"),
            rs.getInt("per_email_limit"),
            rs.getInt("global_usage"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public CouponAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existsByCode(String codeNormalized) {
        return jdbc.queryForObject(
                        "SELECT count(*) FROM coupon WHERE code_normalized = ?", Integer.class, codeNormalized)
                > 0;
    }

    public Optional<CouponResponse> find(UUID id) {
        return jdbc.query(SELECT + " WHERE c.id = ?", MAPPER, id).stream().findFirst();
    }

    /** Locks the coupon so a limit change cannot race with a concurrent reservation. */
    public Optional<CouponResponse> findForUpdate(UUID id) {
        jdbc.query("SELECT id FROM coupon WHERE id = ? FOR UPDATE", (rs, row) -> 1, id);
        return find(id);
    }

    public List<CouponResponse> page(int page, int size) {
        return jdbc.query(SELECT + " ORDER BY c.code_normalized LIMIT ? OFFSET ?", MAPPER, size, (long) page * size);
    }

    public long count() {
        return jdbc.queryForObject("SELECT count(*) FROM coupon", Long.class);
    }

    public void insert(
            UUID id,
            String codeNormalized,
            CouponDiscount discount,
            Instant validFrom,
            Instant validUntil,
            Integer globalLimit,
            int perEmailLimit,
            Instant now) {
        jdbc.update(
                "INSERT INTO coupon (id, code_normalized, discount_type, discount_value, minimum_cents, valid_from,"
                        + " valid_until, active, global_limit, per_email_limit, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?)",
                id,
                codeNormalized,
                discount.type().name(),
                discount.value(),
                discount.minimumCents(),
                Timestamp.from(validFrom),
                Timestamp.from(validUntil),
                globalLimit,
                perEmailLimit,
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public void update(
            UUID id,
            CouponDiscount discount,
            Instant validFrom,
            Instant validUntil,
            Integer globalLimit,
            int perEmailLimit,
            boolean active,
            Instant now) {
        jdbc.update(
                "UPDATE coupon SET discount_type = ?, discount_value = ?, minimum_cents = ?, valid_from = ?,"
                        + " valid_until = ?, global_limit = ?, per_email_limit = ?, active = ?, updated_at = ?"
                        + " WHERE id = ?",
                discount.type().name(),
                discount.value(),
                discount.minimumCents(),
                Timestamp.from(validFrom),
                Timestamp.from(validUntil),
                globalLimit,
                perEmailLimit,
                active,
                Timestamp.from(now),
                id);
    }
}
