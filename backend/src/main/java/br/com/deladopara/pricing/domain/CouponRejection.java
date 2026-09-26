package br.com.deladopara.pricing.domain;

/** Stable commercial reasons a coupon cannot be reserved. */
public enum CouponRejection {
    NOT_FOUND,
    INACTIVE,
    NOT_YET_VALID,
    EXPIRED,
    BELOW_MINIMUM,
    EMAIL_LIMIT_REACHED,
    GLOBAL_LIMIT_REACHED
}
