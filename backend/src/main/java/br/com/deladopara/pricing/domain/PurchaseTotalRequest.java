package br.com.deladopara.pricing.domain;

import java.util.List;

public record PurchaseTotalRequest(List<PurchaseLine> lines, Money shipping, CouponDiscount discount) {
    public PurchaseTotalRequest(List<PurchaseLine> lines, long shippingCents, CouponDiscount discount) {
        this(lines, Money.brl(shippingCents), discount);
    }

    public long shippingCents() {
        return shipping.cents();
    }
}
