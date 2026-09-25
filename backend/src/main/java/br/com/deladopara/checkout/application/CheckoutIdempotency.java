package br.com.deladopara.checkout.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable idempotency for purchase acceptance (SPEC-checkout §A2). The claim lives in the acceptance transaction:
 * a rollback removes it, and a concurrent claim of the same key waits on the unique key and then sees the outcome.
 */
@Service
public class CheckoutIdempotency {

    public static final String PURCHASE = "checkout.purchase";
    private static final Pattern KEY = Pattern.compile("[\\x21-\\x7E]{16,160}");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CheckoutIdempotency(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Claim claim(String subject, String operation, String key, String requestHash) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
        var inserted = jdbc.update(
                "INSERT INTO checkout_idempotency (subject, operation, idem_key, request_hash, status, created_at)"
                        + " VALUES (?, ?, ?, ?, 'PENDING', ?) ON CONFLICT DO NOTHING",
                subject,
                operation,
                key,
                requestHash,
                Timestamp.from(clock.instant()));
        if (inserted == 1) {
            return new Claim(Outcome.NEW, null);
        }
        var existing = jdbc.queryForMap(
                "SELECT request_hash, status, order_id FROM checkout_idempotency"
                        + " WHERE subject = ? AND operation = ? AND idem_key = ?",
                subject,
                operation,
                key);
        if (!requestHash.equals(existing.get("request_hash"))) {
            throw new IdempotencyKeyReusedException();
        }
        if ("PENDING".equals(existing.get("status"))) {
            throw new PurchaseInProgressException();
        }
        return new Claim(Outcome.REPLAY, (UUID) existing.get("order_id"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(String subject, String operation, String key, UUID orderId) {
        var updated = jdbc.update(
                "UPDATE checkout_idempotency SET status = 'COMPLETED', order_id = ?, completed_at = ?"
                        + " WHERE subject = ? AND operation = ? AND idem_key = ? AND status = 'PENDING'",
                orderId,
                Timestamp.from(clock.instant()),
                subject,
                operation,
                key);
        if (updated != 1) {
            throw new IllegalStateException("Idempotency claim is not pending");
        }
    }

    /**
     * SHA-256 over the purchase intent fields in a fixed order. Null fields are encoded distinctly from empty ones,
     * and a separator that cannot appear in the values keeps field boundaries unambiguous.
     */
    public static String purchaseHash(
            UUID snapshotId,
            long cartVersion,
            String deliverySelection,
            String email,
            String couponCode,
            String summaryVersion) {
        var fields = List.of(
                String.valueOf(Objects.requireNonNull(snapshotId)),
                Long.toString(cartVersion),
                encode(deliverySelection),
                encode(email),
                encode(couponCode),
                encode(summaryVersion));
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(fields.stream()
                            .collect(Collectors.joining("\u001f"))
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String value) {
        return value == null ? "\u0000" : value;
    }

    public enum Outcome {
        NEW,
        REPLAY
    }

    public record Claim(Outcome outcome, UUID orderId) {}

    public static class InvalidIdempotencyKeyException extends RuntimeException {}

    public static class IdempotencyKeyReusedException extends RuntimeException {}

    public static class PurchaseInProgressException extends RuntimeException {}
}
