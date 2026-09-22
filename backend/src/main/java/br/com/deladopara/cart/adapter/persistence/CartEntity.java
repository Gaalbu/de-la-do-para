package br.com.deladopara.cart.adapter.persistence;

import br.com.deladopara.cart.application.CartConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class CartEntity {

    @Id
    private UUID id;

    @Column(name = "guest_session_key", length = 160)
    private String guestSessionKey;

    @Column(name = "account_id")
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItemEntity> items = new ArrayList<>();

    protected CartEntity() {}

    public CartEntity(UUID id, String guestSessionKey, UUID accountId, Instant now) {
        this.id = id;
        this.guestSessionKey = guestSessionKey;
        this.accountId = accountId;
        this.status = Status.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getGuestSessionKey() {
        return guestSessionKey;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Status getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<CartItemEntity> getItems() {
        return items;
    }

    public void requireWritable(long expectedVersion) {
        if (status != Status.OPEN || version != expectedVersion) {
            throw new CartConflictException();
        }
    }

    public void replaceItems(List<CartItemEntity> replacement, Instant now) {
        items.clear();
        items.addAll(replacement);
        updatedAt = now;
    }

    public enum Status {
        OPEN,
        CHECKOUT_STARTED
    }
}
