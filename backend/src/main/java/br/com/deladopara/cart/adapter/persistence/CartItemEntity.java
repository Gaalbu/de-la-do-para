package br.com.deladopara.cart.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
@IdClass(CartItemEntity.Key.class)
public class CartItemEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @Id
    @Column(name = "sku_id", nullable = false)
    private UUID skuId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CartItemEntity() {}

    public CartItemEntity(CartEntity cart, UUID skuId, int quantity, Instant now) {
        this.cart = cart;
        this.skuId = skuId;
        this.quantity = quantity;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public CartEntity getCart() {
        return cart;
    }

    public UUID getSkuId() {
        return skuId;
    }

    public int getQuantity() {
        return quantity;
    }

    public record Key(UUID cart, UUID skuId) {}
}
