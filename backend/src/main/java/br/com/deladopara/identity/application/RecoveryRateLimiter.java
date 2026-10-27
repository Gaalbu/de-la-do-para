package br.com.deladopara.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Process-local fixed-window limit for password recovery. */
@Component
public class RecoveryRateLimiter {

    private static final int LIMIT = 3;
    private static final int MAX_KEYS = 20_000;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final Clock clock;
    private final Map<String, Window> windows = new LinkedHashMap<>(128, 0.75f, true);

    public RecoveryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized int retryAfterSeconds(String ip, String email) {
        var now = clock.instant();
        windows.entrySet()
                .removeIf(entry -> !now.isBefore(entry.getValue().startedAt().plus(WINDOW)));
        var key = digest((ip == null ? "" : ip) + "\u0000" + (email == null ? "" : email));
        var current = windows.get(key);
        if (current == null) {
            if (windows.size() >= MAX_KEYS) {
                windows.remove(windows.keySet().iterator().next());
            }
            windows.put(key, new Window(now, 1));
            return 0;
        }
        if (current.count() >= LIMIT) {
            return Math.max(1, (int)
                    Duration.between(now, current.startedAt().plus(WINDOW)).toSeconds());
        }
        windows.put(key, new Window(current.startedAt(), current.count() + 1));
        return 0;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record Window(Instant startedAt, int count) {}
}
