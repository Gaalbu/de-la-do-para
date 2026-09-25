package br.com.deladopara.payments.application;

import java.time.Instant;
import java.util.UUID;

/** Port to the hosted-checkout provider. Implementations never run inside a database transaction. */
public interface PaymentProvider {

    /**
     * Creates a hosted checkout. Throws {@link ProviderRejectedException} only when the provider refused the request
     * before any effect; any other exception means the outcome is unknown.
     */
    CreatedCheckout createCheckout(CheckoutRequest request);

    record CheckoutRequest(UUID paymentIntentId, UUID orderId, long amountCents) {}

    record CreatedCheckout(String checkoutId, String url, Instant expiresAt) {}

    class ProviderRejectedException extends RuntimeException {

        private final String reason;

        public ProviderRejectedException(String reason) {
            super(reason);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }
}
