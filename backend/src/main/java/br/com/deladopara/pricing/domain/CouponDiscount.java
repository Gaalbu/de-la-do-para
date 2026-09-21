package br.com.deladopara.pricing.domain;

public record CouponDiscount(Type type, long value) {

    public enum Type {
        PERCENTAGE,
        FIXED
    }

    public static CouponDiscount percentage(long value) {
        return new CouponDiscount(Type.PERCENTAGE, value);
    }

    public static CouponDiscount fixed(long value) {
        return new CouponDiscount(Type.FIXED, value);
    }
}
