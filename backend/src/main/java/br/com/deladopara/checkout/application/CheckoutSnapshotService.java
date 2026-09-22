package br.com.deladopara.checkout.application;

import br.com.deladopara.cart.adapter.persistence.CartRepository;
import br.com.deladopara.cart.application.GuestCartService;
import br.com.deladopara.checkout.adapter.persistence.CheckoutSnapshotEntity;
import br.com.deladopara.checkout.adapter.persistence.CheckoutSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutSnapshotService {

    private final CartRepository carts;
    private final CheckoutSnapshotRepository snapshots;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CheckoutSnapshotService(
            CartRepository carts, CheckoutSnapshotRepository snapshots, ObjectMapper objectMapper, Clock clock) {
        this.carts = carts;
        this.snapshots = snapshots;
        this.objectMapper = objectMapper;
        this.clock = clock;
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
}
