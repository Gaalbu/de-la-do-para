package br.com.deladopara.pricing.adapter.persistence;

import br.com.deladopara.pricing.domain.Currency;
import br.com.deladopara.pricing.domain.Money;
import br.com.deladopara.pricing.domain.SkuPrice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pricing_sku_prices")
public class SkuPriceEntity {

    @Id
    @Column(name = "sku_id", nullable = false, updatable = false)
    private UUID skuId;

    @Column(name = "unit_price_cents", nullable = false)
    private long unitPriceCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkuPriceEntity() {}

    public SkuPriceEntity(UUID skuId, Money unitPrice, Instant updatedAt) {
        if (skuId == null || unitPrice == null || updatedAt == null) {
            throw new IllegalArgumentException("SKU price identity, amount and timestamp are required");
        }
        var price = new SkuPrice(skuId, unitPrice);
        this.skuId = price.skuId();
        this.unitPriceCents = price.unitPrice().cents();
        this.currency = price.unitPrice().currency().name();
        this.updatedAt = updatedAt;
    }

    public UUID getSkuId() {
        return skuId;
    }

    public SkuPrice toDomain() {
        return new SkuPrice(skuId, new Money(Currency.valueOf(currency), unitPriceCents));
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
