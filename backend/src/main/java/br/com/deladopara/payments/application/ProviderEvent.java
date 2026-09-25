package br.com.deladopara.payments.application;

import java.time.Instant;

/** A provider notification after authentication and parsing. The provider may deliver the same one more than once. */
public record ProviderEvent(String eventId, Type type, String checkoutId, long amountCents, Instant occurredAt) {

    public enum Type {
        CHECKOUT_PAID,
        CHECKOUT_CANCELED,
        CHECKOUT_EXPIRED
    }
}
