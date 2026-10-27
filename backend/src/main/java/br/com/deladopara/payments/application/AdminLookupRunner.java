package br.com.deladopara.payments.application;

import br.com.deladopara.payments.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs one lookup an administrator queued: claim (committed), provider read outside any transaction, result
 * (committed). A refund being followed is looked up as a refund; anything else as a checkout. Wired by the worker.
 */
public final class AdminLookupRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminLookupRunner.class);

    private final AdminPaymentLookups lookups;
    private final PaymentProvider payments;
    private final RefundProvider refunds;

    public AdminLookupRunner(AdminPaymentLookups lookups, PaymentProvider payments, RefundProvider refunds) {
        this.lookups = lookups;
        this.payments = payments;
        this.refunds = refunds;
    }

    /** Returns false when no lookup was queued. */
    public boolean runNext() {
        var claimed = lookups.claim();
        if (claimed.isEmpty()) {
            return false;
        }
        var lookup = claimed.get();
        try {
            if (lookup.status() == PaymentStatus.REFUND_REQUESTED) {
                var state = refunds.findRefund(lookup.intentId());
                lookups.recordRefund(lookup, state);
            } else {
                var state = payments.findCheckout(lookup.intentId());
                lookups.recordCheckout(lookup, state);
            }
        } catch (RuntimeException failure) {
            log.warn("administrator lookup {} failed; it proves nothing", lookup.operationId(), failure);
            lookups.recordFailure(lookup, "LOOKUP_FAILED:" + failure.getClass().getSimpleName());
        }
        return true;
    }
}
