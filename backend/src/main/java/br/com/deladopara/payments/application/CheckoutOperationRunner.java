package br.com.deladopara.payments.application;

import br.com.deladopara.payments.application.PaymentProvider.CreatedCheckout;
import br.com.deladopara.payments.application.PaymentProvider.ProviderRejectedException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one CREATE_CHECKOUT operation: claim (committed), provider call outside any transaction, result (committed).
 * Wired with a concrete provider by the worker (C59); not a bean on its own.
 */
public final class CheckoutOperationRunner {

    private static final Logger log = LoggerFactory.getLogger(CheckoutOperationRunner.class);

    private final CheckoutOperations operations;
    private final PaymentProvider provider;

    public CheckoutOperationRunner(CheckoutOperations operations, PaymentProvider provider) {
        this.operations = operations;
        this.provider = provider;
    }

    /** Returns false when there was nothing to run. */
    public boolean runNext() {
        var claimed = operations.claim();
        if (claimed.isEmpty()) {
            return false;
        }
        var operation = claimed.get();
        var correlationId = UUID.randomUUID();
        CreatedCheckout checkout;
        try {
            checkout = provider.createCheckout(operation.request());
        } catch (ProviderRejectedException rejected) {
            log.warn(
                    "payment operation {} rejected by provider: {} (correlationId={})",
                    operation.operationId(),
                    rejected.reason(),
                    correlationId);
            operations.recordRejected(operation, "REJECTED:" + rejected.reason(), correlationId);
            return true;
        } catch (RuntimeException unknown) {
            log.warn(
                    "payment operation {} outcome unknown; it will not be retried (correlationId={})",
                    operation.operationId(),
                    correlationId,
                    unknown);
            operations.recordUnknown(operation, "UNKNOWN:" + unknown.getClass().getSimpleName(), correlationId);
            return true;
        }
        try {
            operations.recordCreated(operation, checkout, correlationId);
        } catch (RuntimeException notRecorded) {
            // The provider acted: keep its id in the log so reconciliation (C64) can find the checkout.
            log.error(
                    "payment operation {} created provider checkout {} but the result was not recorded"
                            + " (correlationId={})",
                    operation.operationId(),
                    checkout.checkoutId(),
                    correlationId,
                    notRecorded);
            operations.recordUnknown(
                    operation, "UNKNOWN:" + notRecorded.getClass().getSimpleName(), correlationId);
        }
        return true;
    }
}
