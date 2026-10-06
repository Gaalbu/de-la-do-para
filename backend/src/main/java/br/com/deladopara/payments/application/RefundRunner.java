package br.com.deladopara.payments.application;

import br.com.deladopara.payments.application.PaymentProvider.ProviderRejectedException;
import br.com.deladopara.payments.application.RefundProvider.RefundState;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one REFUND operation or one refund lookup: claim (committed), provider call outside any transaction, result
 * (committed). Wired with a concrete provider by the worker; not a bean on its own.
 */
public final class RefundRunner {

    private static final Logger log = LoggerFactory.getLogger(RefundRunner.class);

    private final RefundOperations operations;
    private final RefundProvider provider;

    public RefundRunner(RefundOperations operations, RefundProvider provider) {
        this.operations = operations;
        this.provider = provider;
    }

    /** Returns false when no refund was pending. */
    public boolean runNext() {
        var claimed = operations.claim();
        if (claimed.isEmpty()) {
            return false;
        }
        var operation = claimed.get();
        var correlationId = UUID.randomUUID();
        RefundState state;
        try {
            state = provider.refund(operation.request());
        } catch (ProviderRejectedException rejected) {
            log.warn(
                    "refund operation {} rejected by provider: {}; left for the operator (correlationId={})",
                    operation.operationId(),
                    rejected.reason(),
                    correlationId);
            operations.recordRejected(operation, "REJECTED:" + rejected.reason());
            return true;
        } catch (RuntimeException unknown) {
            log.warn(
                    "refund operation {} outcome unknown; it will be looked up, never resent (correlationId={})",
                    operation.operationId(),
                    correlationId,
                    unknown);
            operations.recordUnknown(operation, "UNKNOWN:" + unknown.getClass().getSimpleName());
            return true;
        }
        operations.recordAccepted(operation, state, correlationId);
        return true;
    }

    /** Returns false when no refund lookup was due. */
    public boolean lookUpNext() {
        var claimed = operations.claimLookup();
        if (claimed.isEmpty()) {
            return false;
        }
        var lookup = claimed.get();
        Optional<RefundState> state;
        try {
            state = provider.findRefund(lookup.intentId());
        } catch (RuntimeException failure) {
            log.warn("refund lookup for intent {} failed; it proves nothing", lookup.intentId(), failure);
            operations.recordLookupFailure(
                    lookup, "LOOKUP_FAILED:" + failure.getClass().getSimpleName());
            return true;
        }
        operations.recordLookup(lookup, state);
        return true;
    }
}
