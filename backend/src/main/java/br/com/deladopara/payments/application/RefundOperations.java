package br.com.deladopara.payments.application;

import br.com.deladopara.payments.adapter.persistence.PaymentRepository;
import br.com.deladopara.payments.application.RefundProvider.RefundRequest;
import br.com.deladopara.payments.application.RefundProvider.RefundState;
import br.com.deladopara.payments.domain.OperationKind;
import br.com.deladopara.payments.domain.OperationStatus;
import br.com.deladopara.payments.domain.PaymentStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The durable steps of a full refund (SPEC-payments R08, C67). The REFUND operation is written with the
 * REFUND_REQUESTED transition; the worker claims it, calls the provider outside any transaction and records the
 * answer. The intent becomes REFUNDED only once the provider shows the refund settled. A refund that was accepted but
 * not settled, or whose answer was lost, is never sent again: lookups (QUERY operations, spaced by the C45 backoff)
 * follow it until the provider shows it settled. A refusal leaves the intent REFUND_REQUESTED for the operator (C82a).
 */
@Service
public class RefundOperations {

    public static final String SETTLED = "PROVIDER_REFUNDED";

    private static final int CANDIDATES = 20;

    private final PaymentRepository payments;
    private final PaymentIntentService intents;
    private final Clock clock;
    private final Duration lease;

    public RefundOperations(
            PaymentRepository payments,
            PaymentIntentService intents,
            Clock clock,
            @Value("${payments.worker.lease:PT60S}") Duration lease) {
        this.payments = payments;
        this.intents = intents;
        this.clock = clock;
        this.lease = lease;
    }

    /** Marks the oldest pending REFUND operation IN_FLIGHT and commits before any provider call. */
    @Transactional
    public Optional<Claimed> claim() {
        var now = clock.instant();
        return payments.claimPending(OperationKind.REFUND, now, now.plus(lease)).map(operation -> {
            var intent = payments.find(operation.intentId()).orElseThrow();
            return new Claimed(operation.id(), new RefundRequest(intent.id(), intent.amountCents()));
        });
    }

    @Transactional
    public void recordAccepted(Claimed claimed, RefundState state, UUID correlationId) {
        if (payments.finishOperation(
                claimed.operationId(), OperationStatus.SUCCEEDED, "REFUND:" + state, clock.instant())) {
            settleIf(claimed.request().paymentIntentId(), state, correlationId);
        }
    }

    @Transactional
    public void recordRejected(Claimed claimed, String diagnostic) {
        payments.finishOperation(claimed.operationId(), OperationStatus.FAILED, diagnostic, clock.instant());
    }

    @Transactional
    public void recordUnknown(Claimed claimed, String diagnostic) {
        payments.finishOperation(claimed.operationId(), OperationStatus.UNKNOWN, diagnostic, clock.instant());
    }

    /** Starts the next due refund lookup and commits it before the provider is called. */
    @Transactional
    public Optional<Lookup> claimLookup() {
        var now = clock.instant();
        for (var candidate : payments.lockRefundsAwaitingLookup(CANDIDATES)) {
            // Now that the row is ours, a fresh read sees a lookup another worker committed meanwhile.
            var current = payments.refundAwaitingLookup(candidate.intentId());
            if (current.isEmpty()) {
                continue;
            }
            var refund = current.get();
            if (refund.since()
                    .plus(UnknownPaymentLookups.delay(refund.lookups()))
                    .isAfter(now)) {
                continue;
            }
            var operationId = UUID.randomUUID();
            payments.startQuery(operationId, refund.intentId(), now, now.plus(lease));
            return Optional.of(new Lookup(operationId, refund.intentId()));
        }
        return Optional.empty();
    }

    @Transactional
    public void recordLookup(Lookup lookup, Optional<RefundState> state) {
        var diagnostic = state.map(found -> "REFUND:" + found).orElse("REFUND:NOT_FOUND");
        if (payments.finishOperation(lookup.operationId(), OperationStatus.SUCCEEDED, diagnostic, clock.instant())) {
            state.ifPresent(found -> settleIf(lookup.intentId(), found, UUID.randomUUID()));
        }
    }

    @Transactional
    public void recordLookupFailure(Lookup lookup, String diagnostic) {
        payments.finishOperation(lookup.operationId(), OperationStatus.FAILED, diagnostic, clock.instant());
    }

    private void settleIf(UUID intentId, RefundState state, UUID correlationId) {
        if (state != RefundState.DONE) {
            return;
        }
        var intent = payments.lock(intentId).orElseThrow();
        if (intent.status() == PaymentStatus.REFUND_REQUESTED) {
            intents.transition(intentId, PaymentStatus.REFUNDED, SETTLED, correlationId);
        }
    }

    public record Claimed(UUID operationId, RefundRequest request) {}

    public record Lookup(UUID operationId, UUID intentId) {}
}
