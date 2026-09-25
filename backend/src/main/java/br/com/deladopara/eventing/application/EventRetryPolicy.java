package br.com.deladopara.eventing.application;

import java.time.Duration;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Approved C45 retry policy: exponential backoff (1 s, x2, 1 min cap) with full jitter, 8 attempts for transient
 * failures and a single attempt for invalid events, which go straight to quarantine.
 */
@Component
public class EventRetryPolicy {

    public static final int MAX_TRANSIENT_ATTEMPTS = 8;
    public static final int MAX_INVALID_ATTEMPTS = 1;
    public static final Duration INITIAL_DELAY = Duration.ofSeconds(1);
    public static final Duration MAX_DELAY = Duration.ofMinutes(1);

    private final RandomGenerator random;

    @Autowired
    public EventRetryPolicy() {
        this(RandomGenerator.getDefault());
    }

    public EventRetryPolicy(RandomGenerator random) {
        this.random = random;
    }

    public static FailureKind classify(RuntimeException failure) {
        return failure instanceof InvalidEventEnvelopeException || failure instanceof UnsupportedEventException
                ? FailureKind.INVALID
                : FailureKind.TRANSIENT;
    }

    public static int maxAttempts(FailureKind kind) {
        return kind == FailureKind.INVALID ? MAX_INVALID_ATTEMPTS : MAX_TRANSIENT_ATTEMPTS;
    }

    public boolean exhausted(FailureKind kind, int attemptCount) {
        return attemptCount >= maxAttempts(kind);
    }

    /** Delay ceiling before jitter for the given (1-based) attempt that just failed. */
    public Duration ceiling(int attemptCount) {
        var shift = Math.min(Math.max(attemptCount - 1, 0), 30);
        var millis = INITIAL_DELAY.toMillis() << shift;
        return Duration.ofMillis(Math.min(millis, MAX_DELAY.toMillis()));
    }

    public Duration delay(int attemptCount) {
        return Duration.ofMillis(random.nextLong(ceiling(attemptCount).toMillis() + 1));
    }

    public enum FailureKind {
        TRANSIENT,
        INVALID
    }
}
