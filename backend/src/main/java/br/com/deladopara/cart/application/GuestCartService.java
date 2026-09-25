package br.com.deladopara.cart.application;

import br.com.deladopara.cart.adapter.persistence.CartEntity;
import br.com.deladopara.cart.adapter.persistence.CartItemEntity;
import br.com.deladopara.cart.adapter.persistence.CartRepository;
import br.com.deladopara.cart.adapter.web.dto.CartItemRequest;
import br.com.deladopara.cart.adapter.web.dto.CartItemsRequest;
import br.com.deladopara.cart.adapter.web.dto.CartResponse;
import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestCartService {

    private final CartRepository carts;
    private final ProductSkuRepository skus;
    private final Clock clock;

    public GuestCartService(CartRepository carts, ProductSkuRepository skus, Clock clock) {
        this.carts = carts;
        this.skus = skus;
        this.clock = clock;
    }

    @Transactional
    public CartResponse get(String sessionId) {
        return CartResponse.from(findOrCreate(sessionId));
    }

    @Transactional
    public CartResponse replace(String sessionId, CartItemsRequest request) {
        var cart = findOrCreate(sessionId);
        cart.requireWritable(request.expectedVersion());
        var items = request.items() == null ? List.<CartItemRequest>of() : request.items();
        var skuIds = items.stream().map(CartItemRequest::skuId).toList();
        if (new HashSet<>(skuIds).size() != skuIds.size()) {
            throw new CartInvalidInputException("SKU duplicado no carrinho");
        }
        var now = Instant.now(clock);
        var entities = items.stream()
                .map(item -> {
                    skus.findById(item.skuId())
                            .filter(sku -> sku.isActive())
                            .orElseThrow(() -> new CartInvalidInputException("SKU não encontrado ou inativo"));
                    return new CartItemEntity(cart, item.skuId(), item.quantity(), now);
                })
                .toList();
        cart.replaceItems(entities, now);
        return CartResponse.from(carts.saveAndFlush(cart));
    }

    @Transactional
    public CartResponse remove(String sessionId, UUID skuId, long expectedVersion) {
        var cart = find(sessionId);
        cart.requireWritable(expectedVersion);
        cart.getItems().removeIf(item -> item.getSkuId().equals(skuId));
        cart.replaceItems(List.copyOf(cart.getItems()), Instant.now(clock));
        return CartResponse.from(carts.saveAndFlush(cart));
    }

    @Transactional
    public CartResponse clear(String sessionId, long expectedVersion) {
        var cart = find(sessionId);
        cart.requireWritable(expectedVersion);
        cart.replaceItems(List.of(), Instant.now(clock));
        return CartResponse.from(carts.saveAndFlush(cart));
    }

    /**
     * Removes the purchased quantities after checkout; lines added after the snapshot, and any quantity above the
     * purchased one, stay in the cart.
     */
    @Transactional
    public void consumePurchased(String sessionId, Map<UUID, Integer> purchased) {
        var cart = find(sessionId);
        var now = Instant.now(clock);
        var remaining = new ArrayList<CartItemEntity>();
        for (var item : cart.getItems()) {
            var left = item.getQuantity() - purchased.getOrDefault(item.getSkuId(), 0);
            if (left > 0) {
                remaining.add(new CartItemEntity(cart, item.getSkuId(), left, now));
            }
        }
        cart.replaceItems(remaining, now);
        carts.saveAndFlush(cart);
    }

    private CartEntity findOrCreate(String sessionId) {
        var key = hashSession(sessionId);
        return carts.findByGuestSessionKey(key)
                .orElseGet(() -> carts.save(new CartEntity(UUID.randomUUID(), key, null, Instant.now(clock))));
    }

    private CartEntity find(String sessionId) {
        return carts.findByGuestSessionKey(hashSession(sessionId)).orElseThrow(CartNotFoundException::new);
    }

    public static String hashSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new CartNotFoundException();
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não disponível", exception);
        }
    }
}
