package br.com.deladopara.payments.application;

import br.com.deladopara.payments.application.PaymentProvider.ProviderRejectedException;
import java.util.UUID;

/**
 * Runs one CREATE_CHECKOUT operation: claim (committed), provider call outside any transaction, result (committed).
 * Wired with a concrete provider by the worker (C59); not a bean on its own.
 */
public final class CheckoutOperationRunner {

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
        var correlationId = UUID.randomUUID();
        try {
            var checkout = provider.createCheckout(claimed.get().request());
            operations.recordCreated(claimed.get(), checkout, correlationId);
        } catch (ProviderRejectedException rejected) {
            operations.recordRejected(claimed.get(), "REJECTED:" + rejected.reason(), correlationId);
        } catch (RuntimeException unknown) {
            operations.recordUnknown(
                    claimed.get(), "UNKNOWN:" + unknown.getClass().getSimpleName(), correlationId);
        }
        return true;
    }
}
