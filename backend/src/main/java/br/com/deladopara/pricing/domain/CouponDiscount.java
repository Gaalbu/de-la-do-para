package br.com.deladopara.pricing.domain;

public record CouponDiscount(Type type, long value, long minimumCents) {

    public CouponDiscount {
        if (type == null || value < 0 || minimumCents < 0) {
            throw new IllegalArgumentException("Invalid coupon discount");
        }
    }

    public CouponDiscount(Type type, long value) {
        this(type, value, 0);
    }

    public enum Type {
        PERCENTAGE,
        FIXED
    }

    public static CouponDiscount percentage(long value) {
        return new CouponDiscount(Type.PERCENTAGE, value);
    }

    public static CouponDiscount percentage(long value, long minimumCents) {
        return new CouponDiscount(Type.PERCENTAGE, value, minimumCents);
    }

    public static CouponDiscount fixed(long value) {
        return new CouponDiscount(Type.FIXED, value);
    }

    public static CouponDiscount fixed(long value, long minimumCents) {
        return new CouponDiscount(Type.FIXED, value, minimumCents);
    }
}
