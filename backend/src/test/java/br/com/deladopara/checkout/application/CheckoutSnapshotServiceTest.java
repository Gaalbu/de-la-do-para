package br.com.deladopara.checkout.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.cart.adapter.persistence.CartRepository;
import br.com.deladopara.checkout.adapter.persistence.CheckoutSnapshotEntity;
import br.com.deladopara.checkout.adapter.persistence.CheckoutSnapshotRepository;
import br.com.deladopara.shipping.application.DeliveryOptionsService;
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
}
