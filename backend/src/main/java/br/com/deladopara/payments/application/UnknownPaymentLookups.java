package br.com.deladopara.payments.application;

import br.com.deladopara.payments.adapter.persistence.PaymentRepository;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutState;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutStatus;
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
 * The durable steps of resolving an UNKNOWN intent (C65). The checkout may or may not exist at the provider, so it is
 * never created again: only lookups decide. Each lookup is a QUERY operation (claimed and leased like a creation),
 * which is the operator's audit trail. Lookups are spaced by the approved backoff (1 s doubling, up to 1 min); after
 * {@code payments.reconciliation.max-lookups} inconclusive ones the intent goes to UNDER_REVIEW (PAY-Q02 proposal,
 * pending approval). An empty answer is never read as proof that no checkout exists.
 */
@Service
public class UnknownPaymentLookups {

    public static final String UNRESOLVED = "UNKNOWN_UNRESOLVED";

    static final Duration BASE_DELAY = Duration.ofSeconds(1);

    static final Duration MAX_DELAY = Duration.ofMinutes(1);

    private static final int CANDIDATES = 20;

    private final PaymentRepository payments;
    private final PaymentIntentService intents;
    private final Clock clock;
    private final Duration lease;
    private final int maxLookups;

    public UnknownPaymentLookups(
            PaymentRepository payments,
            PaymentIntentService intents,
            Clock clock,
            @Value("${payments.worker.lease:PT60S}") Duration lease,
            @Value("${payments.reconciliation.max-lookups:3}") int maxLookups) {
        if (maxLookups < 1) {
            throw new IllegalArgumentException("payments.reconciliation.max-lookups must be positive");
        }
        this.payments = payments;
        this.intents = intents;
        this.clock = clock;
        this.lease = lease;
        this.maxLookups = maxLookups;
    }

    /** Wait before lookup number {@code done + 1}. */
    static Duration delay(int done) {
        var step = BASE_DELAY.multipliedBy(1L << Math.min(done, 16));
        return step.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : step;
    }

    /** Starts the next due lookup and commits it before the provider is called. */
    @Transactional
    public Optional<Claimed> claim() {
        var now = clock.instant();
        for (var intent : payments.lockUnknownWithoutActiveQuery(CANDIDATES)) {
            if (intent.since().plus(delay(intent.lookups())).isAfter(now)) {
                continue;
            }
            var operationId = UUID.randomUUID();
            payments.startQuery(operationId, intent.id(), now, now.plus(lease));
            return Optional.of(new Claimed(operationId, intent.id(), intent.amountCents(), intent.lookups() + 1));
        }
        return Optional.empty();
    }

    @Transactional
    public void recordAnswer(Claimed claimed, Optional<CheckoutState> state) {
        var diagnostic = state.map(s -> "FOUND:" + s.status()).orElse("NOT_FOUND");
        conclude(claimed, OperationStatus.SUCCEEDED, diagnostic, state);
    }

    @Transactional
    public void recordFailure(Claimed claimed, String diagnostic) {
        conclude(claimed, OperationStatus.FAILED, diagnostic, Optional.empty());
    }

    private void conclude(Claimed claimed, OperationStatus result, String diagnostic, Optional<CheckoutState> state) {
        if (!payments.finishOperation(claimed.operationId(), result, diagnostic, clock.instant())) {
            return;
        }
        var intent = payments.lock(claimed.intentId()).orElseThrow();
        if (intent.status() != PaymentStatus.UNKNOWN) {
            return;
        }
        state.ifPresent(found -> payments.linkCheckout(claimed.intentId(), found.checkoutId()));
        var paid = state.filter(found -> found.status() == CheckoutStatus.PAID);
        if (paid.isPresent()) {
            var exact = paid.get().amountCents() == claimed.amountCents();
            intents.transition(
                    claimed.intentId(),
                    exact ? PaymentStatus.CONFIRMED : PaymentStatus.UNDER_REVIEW,
                    exact ? null : "AMOUNT_MISMATCH",
                    UUID.randomUUID());
            return;
        }
        if (claimed.lookup() >= maxLookups) {
            intents.transition(claimed.intentId(), PaymentStatus.UNDER_REVIEW, UNRESOLVED, UUID.randomUUID());
        }
    }

    /** {@code lookup} is this lookup's number, starting at 1. */
    public record Claimed(UUID operationId, UUID intentId, long amountCents, int lookup) {}
}
