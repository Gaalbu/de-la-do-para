package br.com.deladopara.eventing.application;

import br.com.deladopara.eventing.adapter.persistence.EventReplayRepository;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Audited replay of quarantined records (SPEC-eventing, C82). An administrator asks with a reason; the worker later
 * consumes the original outbox envelope again through {@link EventConsumptionService}, so the event keeps its id,
 * version and correlation, duplicates stay no-ops and handlers apply their usual business rules. Nothing here edits a
 * handler's decision. A record whose envelope could not be read (no event id) or is no longer in the outbox cannot be
 * replayed.
 */
@Service
public class EventReplayService {

    private static final Logger log = LoggerFactory.getLogger(EventReplayService.class);

    private final EventReplayRepository replays;
    private final EventConsumptionService consumption;
    private final TransactionTemplate tx;
    private final Clock clock;

    public EventReplayService(
            EventReplayRepository replays, EventConsumptionService consumption, TransactionTemplate tx, Clock clock) {
        this.replays = replays;
        this.consumption = consumption;
        this.tx = tx;
        this.clock = clock;
    }

    /** Queues one replay; a replay still waiting for the worker is reused and {@code created} is false. */
    @Transactional
    public Requested request(String topic, int partition, long offset, String actor, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 200) {
            throw new InvalidReplayReasonException();
        }
        if (!replays.quarantinedExists(topic, partition, offset)) {
            throw new QuarantinedRecordNotFoundException();
        }
        var eventId = replays.lockQuarantinedEventId(topic, partition, offset)
                .orElseThrow(() -> new ReplayNotEligibleException("the record has no readable event id"));
        if (!replays.outboxHas(eventId)) {
            throw new ReplayNotEligibleException("the original event is no longer in the outbox");
        }
        var waiting = replays.pendingRequest(topic, partition, offset);
        if (waiting.isPresent()) {
            return new Requested(waiting.get(), false);
        }
        var id = UUID.randomUUID();
        replays.insertRequest(id, topic, partition, offset, eventId, actor, reason.strip(), clock.instant());
        return new Requested(id, true);
    }

    /**
     * Replays the oldest waiting request; returns false when there was none. The consumption and the request's outcome
     * commit together; if a handler fails, that work is rolled back and the request is closed as FAILED instead.
     */
    public boolean replayNext() {
        var claimed = new AtomicReference<UUID>();
        try {
            return Boolean.TRUE.equals(tx.execute(status -> {
                var pending = replays.lockOldestPending();
                if (pending.isEmpty()) {
                    return false;
                }
                claimed.set(pending.get().id());
                var envelope = replays.outboxEnvelope(pending.get().eventId())
                        .orElseThrow(() -> new ReplayNotEligibleException("the original event left the outbox"));
                var previous = MDC.get("correlationId");
                MDC.put("correlationId", envelope.correlationId().toString());
                try {
                    var outcome = consumption.consume(envelope);
                    var applied = outcome == EventConsumptionOutcome.DUPLICATE ? "DUPLICATE" : "APPLIED";
                    replays.finish(pending.get().id(), applied, outcome.name(), clock.instant());
                } finally {
                    if (previous == null) {
                        MDC.remove("correlationId");
                    } else {
                        MDC.put("correlationId", previous);
                    }
                }
                return true;
            }));
        } catch (RuntimeException failure) {
            if (claimed.get() == null) {
                throw failure;
            }
            log.warn("event replay {} failed; the record stays quarantined", claimed.get(), failure);
            tx.executeWithoutResult(status -> replays.finish(
                    claimed.get(), "FAILED", "FAILED:" + failure.getClass().getSimpleName(), clock.instant()));
            return true;
        }
    }

    public record Requested(UUID requestId, boolean created) {}

    public static class InvalidReplayReasonException extends RuntimeException {}

    public static class QuarantinedRecordNotFoundException extends RuntimeException {}

    public static class ReplayNotEligibleException extends RuntimeException {

        public ReplayNotEligibleException(String message) {
            super(message);
        }
    }
}
