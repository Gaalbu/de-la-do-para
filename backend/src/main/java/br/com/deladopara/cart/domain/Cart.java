package br.com.deladopara.cart.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Cart {

    private final UUID id;
    private final String guestSessionKey;
    private final UUID accountId;
    private Status status;
    private long version;
    private Instant updatedAt;
    private List<CartItem> items = List.of();

    public Cart(UUID id, String guestSessionKey, UUID accountId, Instant now) {
        if (id == null || now == null || (guestSessionKey == null) == (accountId == null)) {
            throw new IllegalArgumentException("cart requires exactly one owner and timestamps");
        }
        this.id = id;
        this.guestSessionKey = guestSessionKey;
        this.accountId = accountId;
        this.status = Status.OPEN;
        this.updatedAt = now;
    }

    public void requireVersion(long expectedVersion) {
        if (version != expectedVersion) {
            throw new CartConflictException();
        }
        if (status != Status.OPEN) {
            throw new CartConflictException();
        }
    }

    public void applyItems(List<CartItem> replacement, long expectedVersion, Instant now) {
        if (replacement == null
                || now == null
                || replacement.stream()
                        .anyMatch(item -> item == null || item.skuId() == null || item.quantity() <= 0)) {
            throw new IllegalArgumentException("cart items must have positive quantities");
        }
        requireVersion(expectedVersion);
        items = List.copyOf(new ArrayList<>(replacement));
        version++;
        updatedAt = now;
    }

    public void startCheckout(long expectedVersion, Instant now) {
        requireVersion(expectedVersion);
        status = Status.CHECKOUT_STARTED;
        version++;
        updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public String guestSessionKey() {
        return guestSessionKey;
    }

    public UUID accountId() {
        return accountId;
    }

    public Status status() {
        return status;
    }

    public long version() {
        return version;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<CartItem> items() {
        return items;
    }

    public enum Status {
        OPEN,
        CHECKOUT_STARTED
    }

    public record CartItem(UUID skuId, int quantity) {}

    public static class CartConflictException extends RuntimeException {}
}
