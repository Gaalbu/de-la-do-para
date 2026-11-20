package br.com.deladopara.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.cart.adapter.persistence.CartRepository;
import br.com.deladopara.checkout.adapter.persistence.CheckoutSnapshotEntity;
import br.com.deladopara.checkout.adapter.persistence.CheckoutSnapshotRepository;
import br.com.deladopara.shipping.application.DeliveryOptionsService;
import br.com.deladopara.shipping.application.ShippingQuote;
import br.com.deladopara.shipping.application.ShippingQuoteGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CheckoutSnapshotServiceTest {

    @Test
    void refusesCheckoutWhenGuestCartDoesNotExist() {
        var service = new CheckoutSnapshotService(
                Mockito.mock(CartRepository.class),
                Mockito.mock(CheckoutSnapshotRepository.class),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC),
                Mockito.mock(DeliveryOptionsService.class));

        assertThatThrownBy(() -> service.start("guest-session")).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void findsDeliveryOptionsOnlyForOwnedSnapshotVersion() {
        var carts = Mockito.mock(CartRepository.class);
        var snapshots = Mockito.mock(CheckoutSnapshotRepository.class);
        var deliveryOptions = Mockito.mock(DeliveryOptionsService.class);
        var snapshotId = UUID.randomUUID();
        var sessionId = "session-id";
        var sessionKey = br.com.deladopara.cart.application.GuestCartService.hashSession(sessionId);
        var snapshot = new CheckoutSnapshotEntity(
                snapshotId, UUID.randomUUID(), 4, sessionKey, "[]", Instant.parse("2026-09-22T12:00:00Z"));
        Mockito.when(snapshots.findByIdAndGuestSessionKey(snapshotId, sessionKey))
                .thenReturn(java.util.Optional.of(snapshot));
        var service = new CheckoutSnapshotService(
                carts,
                snapshots,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC),
                deliveryOptions);

        service.findDeliveryOptions(sessionId, snapshotId, 4, "66053-000");

        Mockito.verify(deliveryOptions).findAvailable(snapshotId, 4, "66053-000");
    }

    @Test
    void delegatesDeliveryOptionsToQuoteGenerationForOwnedSnapshot() {
        var carts = Mockito.mock(CartRepository.class);
        var snapshots = Mockito.mock(CheckoutSnapshotRepository.class);
        var snapshotId = UUID.randomUUID();
        var sessionId = "session-id";
        var sessionKey = br.com.deladopara.cart.application.GuestCartService.hashSession(sessionId);
        var snapshot = new CheckoutSnapshotEntity(
                snapshotId, UUID.randomUUID(), 4, sessionKey, "[]", Instant.parse("2026-09-22T12:00:00Z"));
        Mockito.when(snapshots.findByIdAndGuestSessionKey(snapshotId, sessionKey))
                .thenReturn(java.util.Optional.of(snapshot));
        var generation = Mockito.mock(br.com.deladopara.shipping.application.ShippingQuoteGenerationService.class);
        var service = new CheckoutSnapshotService(
                carts,
                snapshots,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC),
                Mockito.mock(DeliveryOptionsService.class),
                Mockito.mock(PickupOptionsService.class),
                generation);

        service.findDeliveryOptions(sessionId, snapshotId, 4, "66053-000");

        Mockito.verify(generation).findOrCreate(snapshotId, 4, "[]", "66053-000");
    }

    @Test
    void selectsDeliveryOnlyForOwnedCurrentSnapshot() {
        var snapshots = Mockito.mock(CheckoutSnapshotRepository.class);
        var deliveryOptions = Mockito.mock(DeliveryOptionsService.class);
        var snapshotId = UUID.randomUUID();
        var sessionId = "session-id";
        var sessionKey = br.com.deladopara.cart.application.GuestCartService.hashSession(sessionId);
        var snapshot = new CheckoutSnapshotEntity(
                snapshotId, UUID.randomUUID(), 4, sessionKey, "[]", Instant.parse("2026-09-22T12:00:00Z"));
        var quoteId = UUID.randomUUID();
        var quote = new ShippingQuote(
                quoteId,
                snapshotId,
                4,
                "66053000",
                "fingerprint",
                "pac",
                "PAC",
                1000,
                5,
                1,
                java.util.List.of(1),
                Instant.parse("2026-09-22T12:00:00Z"),
                Instant.parse("2026-09-22T13:00:00Z"));
        Mockito.when(snapshots.findByIdAndGuestSessionKey(snapshotId, sessionKey))
                .thenReturn(java.util.Optional.of(snapshot));
        Mockito.when(deliveryOptions.select(snapshotId, 4, quoteId, "fingerprint"))
                .thenReturn(quote);
        var service = new CheckoutSnapshotService(
                Mockito.mock(CartRepository.class),
                snapshots,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC),
                deliveryOptions);

        assertThat(service.selectDeliveryOption(sessionId, snapshotId, 4, quoteId, "fingerprint"))
                .isSameAs(quote);
        Mockito.verify(deliveryOptions).select(snapshotId, 4, quoteId, "fingerprint");
    }

    @Test
    void validatesSelectedQuoteAgainstCurrentCatalogAndSnapshotInputs() {
        var snapshots = Mockito.mock(CheckoutSnapshotRepository.class);
        var deliveryOptions = Mockito.mock(DeliveryOptionsService.class);
        var shippingQuotes = Mockito.mock(ShippingQuoteGenerationService.class);
        var snapshotId = UUID.randomUUID();
        var sessionId = "session-id";
        var sessionKey = br.com.deladopara.cart.application.GuestCartService.hashSession(sessionId);
        var snapshot = new CheckoutSnapshotEntity(
                snapshotId, UUID.randomUUID(), 4, sessionKey, "[]", Instant.parse("2026-09-22T12:00:00Z"));
        var quoteId = UUID.randomUUID();
        var quote = new ShippingQuote(
                quoteId,
                snapshotId,
                4,
                "66053000",
                "fingerprint",
                "pac",
                "PAC",
                1000,
                5,
                1,
                java.util.List.of(1),
                Instant.parse("2026-09-22T12:00:00Z"),
                Instant.parse("2026-09-22T13:00:00Z"));
        Mockito.when(snapshots.findByIdAndGuestSessionKey(snapshotId, sessionKey))
                .thenReturn(java.util.Optional.of(snapshot));
        Mockito.when(deliveryOptions.select(snapshotId, 4, quoteId, "fingerprint"))
                .thenReturn(quote);
        var service = new CheckoutSnapshotService(
                Mockito.mock(CartRepository.class),
                snapshots,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC),
                deliveryOptions,
                Mockito.mock(PickupOptionsService.class),
                shippingQuotes);

        assertThat(service.selectDeliveryOption(sessionId, snapshotId, 4, quoteId, "fingerprint"))
                .isSameAs(quote);
        Mockito.verify(shippingQuotes).assertCurrent(snapshotId, 4, "[]", quote);
    }

    @Test
    void rejectsSelectionWhenCurrentQuoteInputChanged() {
        var snapshots = Mockito.mock(CheckoutSnapshotRepository.class);
        var deliveryOptions = Mockito.mock(DeliveryOptionsService.class);
        var shippingQuotes = Mockito.mock(ShippingQuoteGenerationService.class);
        var snapshotId = UUID.randomUUID();
        var sessionId = "session-id";
        var sessionKey = br.com.deladopara.cart.application.GuestCartService.hashSession(sessionId);
        var snapshot = new CheckoutSnapshotEntity(
                snapshotId, UUID.randomUUID(), 4, sessionKey, "[]", Instant.parse("2026-09-22T12:00:00Z"));
        var quoteId = UUID.randomUUID();
        var quote = new ShippingQuote(
                quoteId,
                snapshotId,
                4,
                "66053000",
                "fingerprint",
                "pac",
                "PAC",
                1000,
                5,
                1,
                java.util.List.of(1),
                Instant.parse("2026-09-22T12:00:00Z"),
                Instant.parse("2026-09-22T13:00:00Z"));
        Mockito.when(snapshots.findByIdAndGuestSessionKey(snapshotId, sessionKey))
                .thenReturn(java.util.Optional.of(snapshot));
        Mockito.when(deliveryOptions.select(snapshotId, 4, quoteId, "fingerprint"))
                .thenReturn(quote);
        Mockito.doThrow(new DeliveryOptionsService.SelectionConflictException())
                .when(shippingQuotes)
                .assertCurrent(snapshotId, 4, "[]", quote);
        var service = new CheckoutSnapshotService(
                Mockito.mock(CartRepository.class),
                snapshots,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC),
                deliveryOptions,
                Mockito.mock(PickupOptionsService.class),
                shippingQuotes);

        assertThatThrownBy(() -> service.selectDeliveryOption(sessionId, snapshotId, 4, quoteId, "fingerprint"))
                .isInstanceOf(DeliveryOptionsService.SelectionConflictException.class);
    }
}
