package br.com.deladopara.identity.adapter.web;

import br.com.deladopara.identity.application.AccountService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IdentityRecoveryRateLimiter {

    private static final int LIMIT = 3;
    private static final int MAX_KEYS = 10_000;
    private static final Duration WINDOW = Duration.ofHours(1);
    private final Clock clock;
    private final Map<String, ArrayDeque<Instant>> attempts = new LinkedHashMap<>();

    public IdentityRecoveryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void acquire(String ip, String rawEmail) {
        var key = digest((ip == null ? "unknown" : ip) + "\u0000" + AccountService.normalize(rawEmail));
        var now = clock.instant();
        var bucket = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        while (!bucket.isEmpty() && !bucket.getFirst().plus(WINDOW).isAfter(now)) {
            bucket.removeFirst();
        }
        if (bucket.size() >= LIMIT) {
            var seconds = Math.max(
                    1, Duration.between(now, bucket.getFirst().plus(WINDOW)).toSeconds());
            throw new RateLimitExceededException(seconds);
        }
        bucket.addLast(now);
        if (attempts.size() > MAX_KEYS) {
            var iterator = attempts.entrySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(value.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class RateLimitExceededException extends RuntimeException {
        private final long retryAfterSeconds;

        public RateLimitExceededException(long retryAfterSeconds) {
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
