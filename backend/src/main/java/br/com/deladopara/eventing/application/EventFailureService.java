package br.com.deladopara.eventing.application;

import br.com.deladopara.eventing.adapter.persistence.EventFailureRepository;
import br.com.deladopara.eventing.adapter.persistence.EventFailureRepository.Failure;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable retry/quarantine bookkeeping for inbound records that failed processing. */
@Service
public class EventFailureService {

    private final EventFailureRepository failures;
    private final EventRetryPolicy policy;
    private final Clock clock;

    public EventFailureService(EventFailureRepository failures, EventRetryPolicy policy, Clock clock) {
        this.failures = failures;
        this.policy = policy;
        this.clock = clock;
    }

    /**
     * Records the failed attempt. The stored diagnostic is the exception class only: messages may echo payload data.
     */
    @Transactional
    public FailureDecision recordFailure(Failure failure, RuntimeException cause) {
        var kind = EventRetryPolicy.classify(cause);
        var attempts = failures.attemptCount(failure.topic(), failure.partition(), failure.offset()) + 1;
        var now = clock.instant();
        var error = kind + ":" + cause.getClass().getSimpleName();
        if (policy.exhausted(kind, attempts)) {
            failures.recordQuarantine(failure, kind.name(), attempts, error, now);
            return FailureDecision.quarantined(attempts);
        }
        Instant nextAttemptAt = now.plus(policy.delay(attempts));
        failures.recordRetry(failure, kind.name(), attempts, nextAttemptAt, error, now);
        return FailureDecision.retryAt(attempts, nextAttemptAt);
    }

    public record FailureDecision(boolean quarantined, int attempts, Instant nextAttemptAt) {

        static FailureDecision quarantined(int attempts) {
            return new FailureDecision(true, attempts, null);
        }

        static FailureDecision retryAt(int attempts, Instant nextAttemptAt) {
            return new FailureDecision(false, attempts, nextAttemptAt);
        }
    }
}
