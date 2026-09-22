package br.com.deladopara.checkout.application;

import br.com.deladopara.cart.adapter.persistence.CartRepository;
import br.com.deladopara.cart.application.GuestCartService;
import br.com.deladopara.checkout.adapter.persistence.CheckoutSnapshotEntity;
import br.com.deladopara.checkout.adapter.persistence.CheckoutSnapshotRepository;
import br.com.deladopara.shipping.application.DeliveryOptionsService;
import br.com.deladopara.shipping.application.ShippingQuote;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutSnapshotService {

    private final CartRepository carts;
    private final CheckoutSnapshotRepository snapshots;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final DeliveryOptionsService deliveryOptions;

    public CheckoutSnapshotService(
            CartRepository carts,
            CheckoutSnapshotRepository snapshots,
            ObjectMapper objectMapper,
            Clock clock,
            DeliveryOptionsService deliveryOptions) {
        this.carts = carts;
        this.snapshots = snapshots;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.deliveryOptions = deliveryOptions;
    }

    @Transactional(readOnly = true)
    public List<ShippingQuote> findDeliveryOptions(
            String sessionId, UUID snapshotId, long snapshotVersion, String destinationPostalCode) {
        var sessionKey = GuestCartService.hashSession(sessionId);
        var snapshot =
                snapshots.findByIdAndGuestSessionKey(snapshotId, sessionKey).orElseThrow();
        if (snapshot.getCartVersion() != snapshotVersion) {
            throw new SnapshotVersionConflictException();
        }
        return deliveryOptions.findAvailable(snapshotId, snapshotVersion, destinationPostalCode);
    }

    @Transactional(readOnly = true)
    public ShippingQuote selectDeliveryOption(
            String sessionId, UUID snapshotId, long snapshotVersion, UUID quoteId, String inputFingerprint) {
        var sessionKey = GuestCartService.hashSession(sessionId);
        var snapshot =
                snapshots.findByIdAndGuestSessionKey(snapshotId, sessionKey).orElseThrow();
        if (snapshot.getCartVersion() != snapshotVersion) {
            throw new SnapshotVersionConflictException();
        }
        return deliveryOptions.select(snapshotId, snapshotVersion, quoteId, inputFingerprint);
    }

    @Transactional
    public CheckoutSnapshotEntity start(String sessionId) {
        var sessionKey = GuestCartService.hashSession(sessionId);
        var cart = carts.findByGuestSessionKey(sessionKey).orElseThrow();
        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("cannot start checkout with an empty cart");
        }
        try {
            var items = cart.getItems().stream()
                    .map(item -> new Item(item.getSkuId(), item.getQuantity()))
                    .toList();
            var entity = new CheckoutSnapshotEntity(
                    UUID.randomUUID(),
                    cart.getId(),
                    cart.getVersion(),
                    sessionKey,
                    objectMapper.writeValueAsString(items),
                    Instant.now(clock));
            return snapshots.save(entity);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("checkout snapshot serialization failed", exception);
        }
    }

    public record Item(UUID skuId, int quantity) {}

    public static class SnapshotVersionConflictException extends RuntimeException {}
}
