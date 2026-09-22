package br.com.deladopara.pricing.domain;

import java.util.UUID;

/** Current sell price owned by pricing and referenced by storefront queries. */
public record SkuPrice(UUID skuId, Money unitPrice) {

    public SkuPrice {
        if (skuId == null) {
            throw new IllegalArgumentException("SKU identity is required");
        }
        if (unitPrice == null || unitPrice.currency() != Currency.BRL || unitPrice.cents() <= 0) {
            throw new IllegalArgumentException("SKU price must be positive");
        }
    }
}
