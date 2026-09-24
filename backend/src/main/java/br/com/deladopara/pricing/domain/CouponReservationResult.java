package br.com.deladopara.pricing.domain;

import java.util.UUID;

public record CouponReservationResult(
        boolean reserved, UUID usageId, CouponDiscount discount, CouponRejection rejection, boolean replayed) {

    public static CouponReservationResult reserved(UUID usageId, CouponDiscount discount, boolean replayed) {
        return new CouponReservationResult(true, usageId, discount, null, replayed);
    }

    public static CouponReservationResult rejected(CouponRejection rejection) {
        return new CouponReservationResult(false, null, null, rejection, false);
    }
}
