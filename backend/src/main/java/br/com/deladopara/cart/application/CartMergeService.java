package br.com.deladopara.cart.application;

import br.com.deladopara.cart.adapter.persistence.CartItemEntity;
import br.com.deladopara.cart.adapter.persistence.CartRepository;
import br.com.deladopara.cart.adapter.web.dto.CartMergeRequest;
import br.com.deladopara.cart.adapter.web.dto.CartMergeView;
import br.com.deladopara.cart.adapter.web.dto.CartResponse;
import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartMergeService {
    private final CartRepository carts;
    private final ProductSkuRepository skus;
    private final Clock clock;

    public CartMergeService(CartRepository carts, ProductSkuRepository skus, Clock clock) {
        this.carts = carts;
        this.skus = skus;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CartMergeView get(UUID accountId, String guestSessionKey) {
        var accountCart = carts.findByAccountId(accountId).orElseThrow(CartNotFoundException::new);
        var guestCart = carts.findByGuestSessionKey(guestSessionKey).orElseThrow(CartNotFoundException::new);
        var skuIds = java.util.stream.Stream.concat(accountCart.getItems().stream(), guestCart.getItems().stream())
                .map(CartItemEntity::getSkuId)
                .distinct()
                .toList();
        var inactiveSkuIds = skus.findAllByIdIn(skuIds).stream()
                .filter(sku -> !sku.isActive())
                .map(sku -> sku.getId())
                .sorted()
                .toList();
        return new CartMergeView(CartResponse.from(accountCart), CartResponse.from(guestCart), inactiveSkuIds);
    }

    @Transactional
    public CartResponse resolve(UUID accountId, String guestSessionKey, CartMergeRequest request) {
        // Lock order is stable across all resolutions to avoid account/guest deadlocks.
        var accountCart = carts.findLockedByAccountId(accountId).orElseThrow(CartNotFoundException::new);
        var guestCart = carts.findLockedByGuestSessionKey(guestSessionKey).orElseThrow(CartNotFoundException::new);
        accountCart.requireWritable(request.expectedAccountVersion());
        guestCart.requireWritable(request.expectedGuestVersion());

        if (request.choice() == CartMergeRequest.Choice.COMBINE) {
            var quantities = new LinkedHashMap<UUID, Integer>();
            accountCart.getItems().forEach(item -> quantities.put(item.getSkuId(), item.getQuantity()));
            for (var item : guestCart.getItems()) {
                try {
                    quantities.merge(item.getSkuId(), item.getQuantity(), Math::addExact);
                } catch (ArithmeticException overflow) {
                    throw new CartConflictException();
                }
            }
            var now = Instant.now(clock);
            accountCart.replaceItems(
                    quantities.entrySet().stream()
                            .map(entry -> new CartItemEntity(accountCart, entry.getKey(), entry.getValue(), now))
                            .toList(),
                    now);
        } else if (request.choice() == CartMergeRequest.Choice.REPLACE_WITH_GUEST) {
            var now = Instant.now(clock);
            accountCart.replaceItems(
                    guestCart.getItems().stream()
                            .map(item -> new CartItemEntity(accountCart, item.getSkuId(), item.getQuantity(), now))
                            .toList(),
                    now);
        }

        carts.delete(guestCart);
        carts.flush();
        return CartResponse.from(accountCart);
    }
}
