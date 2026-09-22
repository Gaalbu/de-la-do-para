package br.com.deladopara.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartTest {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Test
    void incrementsVersionOnlyAfterAnOptimisticUpdate() {
        var cart = new Cart(UUID.randomUUID(), "guest-session-hash", null, NOW);
        cart.applyItems(List.of(new Cart.CartItem(UUID.randomUUID(), 2)), 0, NOW.plusSeconds(1));
        assertThat(cart.version()).isEqualTo(1);
    }

    @Test
    void rejectsStaleVersionsAndMutationsAfterCheckoutStarts() {
        var cart = new Cart(UUID.randomUUID(), "guest-session-hash", null, NOW);
        assertThatThrownBy(() -> cart.applyItems(List.of(new Cart.CartItem(UUID.randomUUID(), 1)), 1, NOW))
                .isInstanceOf(Cart.CartConflictException.class);
        cart.startCheckout(0, NOW.plusSeconds(1));
        assertThatThrownBy(() -> cart.applyItems(List.of(new Cart.CartItem(UUID.randomUUID(), 1)), 1, NOW))
                .isInstanceOf(Cart.CartConflictException.class);
    }

    @Test
    void requiresExactlyOneOwner() {
        assertThatThrownBy(() -> new Cart(UUID.randomUUID(), null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cart(UUID.randomUUID(), "guest", UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
