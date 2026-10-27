package br.com.deladopara.payments.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for full refunds of a paid hosted checkout (D12). Implementations never run inside a database transaction.
 * Throws {@link PaymentProvider.ProviderRejectedException} only when the provider refused before any effect; any other
 * exception means the refund may or may not exist.
 */
public interface RefundProvider {

    /** Requests the full refund of the charge paid for this intent. */
    RefundState refund(RefundRequest request);

    /** Empty only means the provider shows no refund for this intent yet; it never proves the request was lost. */
    Optional<RefundState> findRefund(UUID paymentIntentId);

    record RefundRequest(UUID paymentIntentId, long amountCents) {}

    /** {@code PENDING}: accepted but not settled yet; {@code DONE}: the money went back to the buyer. */
    enum RefundState {
        PENDING,
        DONE
    }
}
