package br.com.deladopara.checkout.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "checkout_snapshots")
public class CheckoutSnapshotEntity {

    @Id
    private UUID id;

    @Column(name = "cart_id", nullable = false, updatable = false)
    private UUID cartId;

    @Column(name = "cart_version", nullable = false, updatable = false)
    private long cartVersion;

    @Column(name = "guest_session_key", nullable = false, length = 160, updatable = false)
    private String guestSessionKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private String items;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected CheckoutSnapshotEntity() {}

    public CheckoutSnapshotEntity(
            UUID id, UUID cartId, long cartVersion, String guestSessionKey, String items, Instant createdAt) {
        this.id = id;
        this.cartId = cartId;
        this.cartVersion = cartVersion;
        this.guestSessionKey = guestSessionKey;
        this.items = items;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCartId() {
        return cartId;
    }

    public long getCartVersion() {
        return cartVersion;
    }

    public String getGuestSessionKey() {
        return guestSessionKey;
    }

    public String getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
