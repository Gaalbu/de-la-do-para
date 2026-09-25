package br.com.deladopara.payments.application;

import br.com.deladopara.payments.adapter.persistence.PaymentRepository;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutRequest;
import br.com.deladopara.payments.application.PaymentProvider.CreatedCheckout;
import br.com.deladopara.payments.domain.OperationStatus;
import br.com.deladopara.payments.domain.PaymentStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The durable steps around one CREATE_CHECKOUT call. Each method is its own short transaction, so the provider call
 * that happens between {@link #claim()} and a {@code record*} method never holds a database transaction.
 */
@Service
public class CheckoutOperations {

    static final Duration LEASE = Duration.ofSeconds(60);

    private final PaymentRepository payments;
    private final PaymentIntentService intents;
    private final Clock clock;

    public CheckoutOperations(PaymentRepository payments, PaymentIntentService intents, Clock clock) {
        this.payments = payments;
        this.intents = intents;
        this.clock = clock;
    }

    /** Marks the oldest pending checkout operation IN_FLIGHT and commits before any provider call. */
    @Transactional
    public Optional<Claimed> claim() {
        var now = clock.instant();
        return payments.claimPendingCheckout(now, now.plus(LEASE)).map(operation -> {
            var intent = payments.find(operation.intentId()).orElseThrow();
            intents.transition(intent.id(), PaymentStatus.CREATING_CHECKOUT, null, UUID.randomUUID());
            return new Claimed(
                    operation.id(), new CheckoutRequest(intent.id(), intent.orderId(), intent.amountCents()));
        });
    }

    @Transactional
    public void recordCreated(Claimed claimed, CreatedCheckout checkout, UUID correlationId) {
        if (!payments.finishOperation(claimed.operationId(), OperationStatus.SUCCEEDED, null, clock.instant())) {
            return;
        }
        var intentId = claimed.request().paymentIntentId();
        payments.storeCheckout(intentId, checkout.checkoutId(), checkout.url(), checkout.expiresAt());
        intents.transition(intentId, PaymentStatus.AWAITING_PAYMENT, null, correlationId);
    }

    @Transactional
    public void recordRejected(Claimed claimed, String diagnostic, UUID correlationId) {
        if (payments.finishOperation(claimed.operationId(), OperationStatus.FAILED, diagnostic, clock.instant())) {
            intents.transition(
                    claimed.request().paymentIntentId(), PaymentStatus.DECLINED, "PROVIDER_REJECTED", correlationId);
        }
    }

    @Transactional
    public void recordUnknown(Claimed claimed, String diagnostic, UUID correlationId) {
        if (payments.finishOperation(claimed.operationId(), OperationStatus.UNKNOWN, diagnostic, clock.instant())) {
            intents.transition(
                    claimed.request().paymentIntentId(), PaymentStatus.UNKNOWN, "OUTCOME_UNKNOWN", correlationId);
        }
    }

    /** An expired lease never authorizes a new call: the request may have reached the provider. */
    @Transactional
    public int recoverAbandoned() {
        var now = clock.instant();
        List<PaymentRepository.Operation> abandoned = payments.lockAbandonedInFlight(now);
        for (var operation : abandoned) {
            payments.finishOperation(operation.id(), OperationStatus.UNKNOWN, "LEASE_EXPIRED", now);
            intents.transition(operation.intentId(), PaymentStatus.UNKNOWN, "LEASE_EXPIRED", UUID.randomUUID());
        }
        return abandoned.size();
    }

    public record Claimed(UUID operationId, CheckoutRequest request) {}
}
