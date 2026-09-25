package br.com.deladopara.payments.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Port to the hosted-checkout provider. Implementations never run inside a database transaction. */
public interface PaymentProvider {

    /**
     * Creates a hosted checkout. Throws {@link ProviderRejectedException} only when the provider refused the request
     * before any effect; any other exception means the outcome is unknown.
     */
    CreatedCheckout createCheckout(CheckoutRequest request);

    /** Looks a checkout up by our intent reference; the only safe way to resolve an unknown creation. */
    Optional<CheckoutState> findCheckout(UUID paymentIntentId);

    record CheckoutRequest(UUID paymentIntentId, UUID orderId, long amountCents) {}

    record CreatedCheckout(String checkoutId, String url, Instant expiresAt) {}

    record CheckoutState(String checkoutId, CheckoutStatus status, long amountCents) {}

    enum CheckoutStatus {
        PENDING,
        PAID,
        CANCELED,
        EXPIRED
    }

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
