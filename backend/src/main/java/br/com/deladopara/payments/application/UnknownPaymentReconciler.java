package br.com.deladopara.payments.application;

import br.com.deladopara.payments.application.PaymentProvider.CheckoutState;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one lookup for an UNKNOWN intent: claim (committed), provider query outside any transaction, answer
 * (committed). Never creates a checkout. Wired with a concrete provider by the worker; not a bean on its own.
 */
public final class UnknownPaymentReconciler {

    private static final Logger log = LoggerFactory.getLogger(UnknownPaymentReconciler.class);

    private final UnknownPaymentLookups lookups;
    private final PaymentProvider provider;

    public UnknownPaymentReconciler(UnknownPaymentLookups lookups, PaymentProvider provider) {
        this.lookups = lookups;
        this.provider = provider;
    }

    /** Returns false when no lookup was due. */
    public boolean runNext() {
        var claimed = lookups.claim();
        if (claimed.isEmpty()) {
            return false;
        }
        var lookup = claimed.get();
        Optional<CheckoutState> state;
        try {
            state = provider.findCheckout(lookup.intentId());
        } catch (RuntimeException failure) {
            log.warn(
                    "lookup {} for unknown payment intent {} failed; it proves nothing",
                    lookup.lookup(),
                    lookup.intentId(),
                    failure);
            lookups.recordFailure(lookup, "LOOKUP_FAILED:" + failure.getClass().getSimpleName());
            return true;
        }
        lookups.recordAnswer(lookup, state);
        return true;
    }
}
