package br.com.deladopara.pricing.domain;

public record PurchaseLine(String skuCode, long unitPriceCents, int quantity) {}
