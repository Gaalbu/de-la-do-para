package br.com.deladopara.payments.application;

import br.com.deladopara.payments.adapter.persistence.PaymentRepository;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutState;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutStatus;
import br.com.deladopara.payments.application.RefundProvider.RefundState;
import br.com.deladopara.payments.domain.OperationKind;
import br.com.deladopara.payments.domain.OperationStatus;
import br.com.deladopara.payments.domain.PaymentStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lookups an administrator asks for on an uncertain payment (SPEC-payments R09, C82a). The request is audited and only
 * enqueues a QUERY operation; the worker claims it and asks the provider outside any transaction, like every other
 * call. A lookup can confirm a payment the provider shows paid for the exact amount, or settle a refund the provider
 * shows done; it never creates a charge or a refund. A divergent amount sends the intent to review instead.
 */
@Service
public class AdminPaymentLookups {

    public static final String ADMIN_LOOKUP = "ADMIN_LOOKUP";

    private static final Set<PaymentStatus> ELIGIBLE =
            EnumSet.of(PaymentStatus.UNKNOWN, PaymentStatus.UNDER_REVIEW, PaymentStatus.REFUND_REQUESTED);

    private final PaymentRepository payments;
    private final PaymentIntentService intents;
    private final Clock clock;
    private final Duration lease;

    public AdminPaymentLookups(
            PaymentRepository payments,
            PaymentIntentService intents,
            Clock clock,
            @Value("${payments.worker.lease:PT60S}") Duration lease) {
        this.payments = payments;
        this.intents = intents;
        this.clock = clock;
        this.lease = lease;
    }

    /**
     * Queues one lookup and records who asked and why. A lookup already waiting for the worker is reused, so a repeated
     * command changes nothing and returns {@code created = false}.
     */
    @Transactional
    public Requested request(UUID intentId, String actor, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 200) {
            throw new InvalidLookupReasonException();
        }
        var intent = payments.lock(intentId).orElseThrow(PaymentIntentService.PaymentIntentNotFoundException::new);
        if (!ELIGIBLE.contains(intent.status())) {
            throw new LookupNotEligibleException(intent.status());
        }
        var waiting = payments.pendingQuery(intentId);
        if (waiting.isPresent()) {
            return new Requested(waiting.get(), false);
        }
        var now = clock.instant();
        var operationId = UUID.randomUUID();
        payments.insertOperation(operationId, intentId, OperationKind.QUERY, now);
        payments.insertAdminLookupRequest(UUID.randomUUID(), intentId, operationId, actor, reason.strip(), now);
        return new Requested(operationId, true);
    }

    /** Claims the oldest queued lookup and commits it before the provider is called. */
    @Transactional
    public Optional<Claimed> claim() {
        var now = clock.instant();
        return payments.claimPending(OperationKind.QUERY, now, now.plus(lease)).map(operation -> {
            var intent = payments.find(operation.intentId()).orElseThrow();
            return new Claimed(operation.id(), intent.id(), intent.amountCents(), intent.status());
        });
    }

    @Transactional
    public void recordCheckout(Claimed claimed, Optional<CheckoutState> state) {
        var diagnostic = state.map(found -> "FOUND:" + found.status()).orElse("NOT_FOUND");
        if (!payments.finishOperation(claimed.operationId(), OperationStatus.SUCCEEDED, diagnostic, clock.instant())) {
            return;
        }
        var intent = payments.lock(claimed.intentId()).orElseThrow();
        if (intent.status() != PaymentStatus.UNKNOWN && intent.status() != PaymentStatus.UNDER_REVIEW) {
            return;
        }
        state.ifPresent(found -> payments.linkCheckout(claimed.intentId(), found.checkoutId()));
        var paid = state.filter(found -> found.status() == CheckoutStatus.PAID);
        if (paid.isEmpty()) {
            return;
        }
        if (paid.get().amountCents() == claimed.amountCents()) {
            intents.transition(claimed.intentId(), PaymentStatus.CONFIRMED, ADMIN_LOOKUP, UUID.randomUUID());
        } else if (intent.status() == PaymentStatus.UNKNOWN) {
            intents.transition(claimed.intentId(), PaymentStatus.UNDER_REVIEW, "AMOUNT_MISMATCH", UUID.randomUUID());
        }
    }

    @Transactional
    public void recordRefund(Claimed claimed, Optional<RefundState> state) {
        var diagnostic = state.map(found -> "REFUND:" + found).orElse("REFUND:NOT_FOUND");
        if (!payments.finishOperation(claimed.operationId(), OperationStatus.SUCCEEDED, diagnostic, clock.instant())) {
            return;
        }
        var intent = payments.lock(claimed.intentId()).orElseThrow();
        if (intent.status() == PaymentStatus.REFUND_REQUESTED && state.orElse(null) == RefundState.DONE) {
            intents.transition(claimed.intentId(), PaymentStatus.REFUNDED, RefundOperations.SETTLED, UUID.randomUUID());
        }
    }

    @Transactional
    public void recordFailure(Claimed claimed, String diagnostic) {
        payments.finishOperation(claimed.operationId(), OperationStatus.FAILED, diagnostic, clock.instant());
    }

    /** {@code status} is the intent's status when the lookup was claimed; it picks what to ask the provider. */
    public record Claimed(UUID operationId, UUID intentId, long amountCents, PaymentStatus status) {}

    public record Requested(UUID operationId, boolean created) {}

    public static class InvalidLookupReasonException extends RuntimeException {}

    public static class LookupNotEligibleException extends RuntimeException {

        public LookupNotEligibleException(PaymentStatus status) {
            super("A lookup is not useful for a payment in status " + status);
        }
    }
}
