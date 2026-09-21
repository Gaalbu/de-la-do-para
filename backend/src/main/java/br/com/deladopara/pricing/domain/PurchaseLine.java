package br.com.deladopara.pricing.domain;

public record PurchaseLine(String skuCode, Money unitPrice, int quantity) {
    public PurchaseLine(String skuCode, long unitPriceCents, int quantity) {
        this(skuCode, Money.brl(unitPriceCents), quantity);
    }

    public long unitPriceCents() {
        return unitPrice.cents();
    }
}
