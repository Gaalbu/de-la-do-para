package br.com.deladopara.pricing.domain;

import java.util.List;

public record PurchaseTotalRequest(List<PurchaseLine> lines, long shippingCents, CouponDiscount discount) {}
