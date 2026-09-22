package br.com.deladopara.pricing.domain;

import java.math.BigInteger;
import java.util.Objects;

public final class PurchaseTotalCalculator {

    private static final BigInteger ONE_HUNDRED = BigInteger.valueOf(100);
    private static final BigInteger HALF = BigInteger.valueOf(50);

    public PurchaseTotal calculate(PurchaseTotalRequest request) {
        Objects.requireNonNull(request, "Purchase total request is required");
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException("At least one purchase line is required");
        }
        if (request.shipping() == null || request.shipping().currency() != Currency.BRL) {
            throw new IllegalArgumentException("Only BRL is supported");
        }

        long subtotal = 0;
        for (PurchaseLine line : request.lines()) {
            validateLine(line);
            long lineTotal;
            try {
                lineTotal = Math.multiplyExact(line.unitPriceCents(), line.quantity());
                subtotal = Math.addExact(subtotal, lineTotal);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Purchase total exceeds supported range", exception);
            }
        }

        long discount = calculateDiscount(subtotal, request.discount());
        try {
            long goodsTotal = Math.subtractExact(subtotal, discount);
            long total = Math.addExact(goodsTotal, request.shippingCents());
            return new PurchaseTotal(subtotal, discount, request.shippingCents(), total);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Purchase total exceeds supported range", exception);
        }
    }

    private static void validateLine(PurchaseLine line) {
        if (line == null || line.skuCode() == null || line.skuCode().isBlank()) {
            throw new IllegalArgumentException("Purchase line SKU is required");
        }
        if (line.unitPrice() == null || line.unitPrice().currency() != Currency.BRL) {
            throw new IllegalArgumentException("Only BRL is supported");
        }
        if (line.quantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    private static long calculateDiscount(long subtotal, CouponDiscount discount) {
        if (discount == null) {
            return 0;
        }
        if (discount.value() < 0) {
            throw new IllegalArgumentException("Coupon value cannot be negative");
        }
        if (subtotal < discount.minimumCents()) {
            throw new IllegalArgumentException("Coupon minimum is not met");
        }
        long requested =
                switch (discount.type()) {
                    case FIXED -> discount.value();
                    case PERCENTAGE -> percentageDiscount(subtotal, discount.value());
                };
        return Math.min(requested, subtotal);
    }

    private static long percentageDiscount(long subtotal, long percentage) {
        if (percentage > 100) {
            throw new IllegalArgumentException("Coupon percentage cannot exceed 100");
        }
        return BigInteger.valueOf(subtotal)
                .multiply(BigInteger.valueOf(percentage))
                .add(HALF)
                .divide(ONE_HUNDRED)
                .longValueExact();
    }
}
