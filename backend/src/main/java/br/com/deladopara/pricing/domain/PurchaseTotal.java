package br.com.deladopara.pricing.domain;

public record PurchaseTotal(long subtotalCents, long discountCents, long shippingCents, long totalCents) {}
