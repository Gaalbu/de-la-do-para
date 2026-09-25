package br.com.deladopara.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.payments.application.PaymentProvider.CheckoutRequest;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutStatus;
import br.com.deladopara.payments.application.PaymentProvider.ProviderRejectedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Behaviour every {@link PaymentProvider} must show to the payments module (SPEC-payments PAY-006…008). The
 * simulator runs it now; the Asaas adapter must run it against recorded HTTP fixtures in C59.
 */
public abstract class PaymentProviderContract {

    protected abstract PaymentProvider provider();

    /** Makes the next creation be refused before any effect. */
    protected abstract void rejectNextCreation();

    /** Makes the next creation take effect at the provider but lose the response. */
    protected abstract void timeOutAfterNextCreation();

    /** Makes the next creation fail before reaching the provider. */
    protected abstract void timeOutBeforeNextCreation();

    /** Pays the checkout on the hosted page and returns the resulting notification. */
    protected abstract ProviderEvent pay(String checkoutId, long amountCents);

    /** Delivers the same notification again. */
    protected abstract ProviderEvent redeliver(ProviderEvent event);

    private static CheckoutRequest request(long amountCents) {
        return new CheckoutRequest(UUID.randomUUID(), UUID.randomUUID(), amountCents);
    }

    @Test
    void createdCheckoutIsPendingForTheExactAmountAndFoundByIntent() {
        var request = request(5_250);

        var created = provider().createCheckout(request);

        assertThat(created.checkoutId()).isNotBlank();
        assertThat(created.url()).startsWith("https://");
        assertThat(created.expiresAt()).isNotNull();
        var state = provider().findCheckout(request.paymentIntentId()).orElseThrow();
        assertThat(state.checkoutId()).isEqualTo(created.checkoutId());
        assertThat(state.status()).isEqualTo(CheckoutStatus.PENDING);
        assertThat(state.amountCents()).isEqualTo(5_250);
    }

    @Test
    void rejectionHappensBeforeAnyEffect() {
        var request = request(5_250);
        rejectNextCreation();

        assertThatThrownBy(() -> provider().createCheckout(request)).isInstanceOf(ProviderRejectedException.class);

        assertThat(provider().findCheckout(request.paymentIntentId())).isEmpty();
    }

    @Test
    void timeoutAfterEffectIsNotARejectionAndTheCheckoutExists() {
        var request = request(5_250);
        timeOutAfterNextCreation();

        assertThatThrownBy(() -> provider().createCheckout(request)).isNotInstanceOf(ProviderRejectedException.class);

        assertThat(provider().findCheckout(request.paymentIntentId()))
                .get()
                .extracting(PaymentProvider.CheckoutState::status)
                .isEqualTo(CheckoutStatus.PENDING);
    }

    @Test
    void timeoutBeforeEffectIsAlsoUnknownToTheCaller() {
        var request = request(5_250);
        timeOutBeforeNextCreation();

        assertThatThrownBy(() -> provider().createCheckout(request)).isNotInstanceOf(ProviderRejectedException.class);

        assertThat(provider().findCheckout(request.paymentIntentId())).isEmpty();
    }

    @Test
    void paymentProducesOneNotificationWhoseRedeliveryKeepsItsIdentity() {
        var request = request(5_250);
        var created = provider().createCheckout(request);

        var paid = pay(created.checkoutId(), 5_250);
        var again = redeliver(paid);

        assertThat(paid.type()).isEqualTo(ProviderEvent.Type.CHECKOUT_PAID);
        assertThat(paid.checkoutId()).isEqualTo(created.checkoutId());
        assertThat(paid.amountCents()).isEqualTo(5_250);
        assertThat(again.eventId()).isEqualTo(paid.eventId());
        assertThat(provider()
                        .findCheckout(request.paymentIntentId())
                        .orElseThrow()
                        .status())
                .isEqualTo(CheckoutStatus.PAID);
    }

    @Test
    void paidAmountIsReportedAsPaidEvenWhenItDiffers() {
        var created = provider().createCheckout(request(5_250));

        assertThat(pay(created.checkoutId(), 5_000).amountCents()).isEqualTo(5_000);
    }
}
