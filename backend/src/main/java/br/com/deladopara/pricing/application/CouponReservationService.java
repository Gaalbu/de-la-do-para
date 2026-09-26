package br.com.deladopara.pricing.application;

import br.com.deladopara.pricing.adapter.persistence.CouponUsageRepository;
import br.com.deladopara.pricing.domain.CouponRejection;
import br.com.deladopara.pricing.domain.CouponReservationResult;
import br.com.deladopara.pricing.domain.CouponUsageState;
import java.time.Clock;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coupon capacity lifecycle: reserve at order creation, consume on confirmed payment, release on expiry/failure,
 * and mark refunded after a confirmed full refund (restores per-email eligibility only; the global limit stays
 * spent). Pricing queries never call this; the caller supplies an already verified e-mail.
 */
@Service
public class CouponReservationService {

    private final CouponUsageRepository coupons;
    private final Clock clock;

    public CouponReservationService(CouponUsageRepository coupons, Clock clock) {
        this.coupons = coupons;
        this.clock = clock;
    }

    public static String normalizeCode(String code) {
        return code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /** Idempotent by {@code reservationKey}: a repeated call returns the original reservation. */
    @Transactional
    public CouponReservationResult reserve(
            String code, String verifiedEmail, long eligibleSubtotalCents, String reservationKey) {
        var existing = coupons.lockUsage(reservationKey);
        if (existing.isPresent()) {
            return replay(existing.get());
        }
        var coupon = coupons.lockByCode(normalizeCode(code)).orElse(null);
        if (coupon == null) {
            return CouponReservationResult.rejected(CouponRejection.NOT_FOUND);
        }
        // A concurrent call with the same key held the coupon lock until it committed; its row is visible now.
        var concurrent = coupons.lockUsage(reservationKey);
        if (concurrent.isPresent()) {
            return replay(concurrent.get());
        }
        var now = clock.instant();
        var rejection = rejectionFor(coupon, verifiedEmail, eligibleSubtotalCents, now);
        if (rejection != null) {
            return CouponReservationResult.rejected(rejection);
        }
        var usageId = coupons.insertReservation(coupon.id(), normalizeEmail(verifiedEmail), reservationKey, now);
        return CouponReservationResult.reserved(usageId, coupon.discount(), false);
    }

    private static CouponReservationResult replay(CouponUsageRepository.UsageRow usage) {
        return usage.state() == CouponUsageState.RELEASED
                ? CouponReservationResult.rejected(CouponRejection.INACTIVE)
                : CouponReservationResult.reserved(usage.id(), usage.discount(), true);
    }

    private CouponRejection rejectionFor(
            CouponUsageRepository.CouponRow coupon, String email, long subtotalCents, java.time.Instant now) {
        if (!coupon.active()) {
            return CouponRejection.INACTIVE;
        }
        if (now.isBefore(coupon.validFrom())) {
            return CouponRejection.NOT_YET_VALID;
        }
        if (!now.isBefore(coupon.validUntil())) {
            return CouponRejection.EXPIRED;
        }
        if (subtotalCents < coupon.discount().minimumCents()) {
            return CouponRejection.BELOW_MINIMUM;
        }
        if (coupons.emailUsage(coupon.id(), normalizeEmail(email)) >= coupon.perEmailLimit()) {
            return CouponRejection.EMAIL_LIMIT_REACHED;
        }
        if (coupon.globalLimit() != null && coupons.globalUsage(coupon.id()) >= coupon.globalLimit()) {
            return CouponRejection.GLOBAL_LIMIT_REACHED;
        }
        return null;
    }

    @Transactional
    public void consume(String reservationKey) {
        move(reservationKey, CouponUsageState.RESERVED, CouponUsageState.CONSUMED);
    }

    @Transactional
    public void release(String reservationKey) {
        move(reservationKey, CouponUsageState.RESERVED, CouponUsageState.RELEASED);
    }

    @Transactional
    public void markFullyRefunded(String reservationKey) {
        move(reservationKey, CouponUsageState.CONSUMED, CouponUsageState.REFUNDED);
    }

    private void move(String reservationKey, CouponUsageState from, CouponUsageState to) {
        var usage = coupons.lockUsage(reservationKey)
                .orElseThrow(() -> new IllegalStateException("Unknown coupon reservation"));
        if (usage.state() == to) {
            return;
        }
        if (usage.state() != from) {
            throw new IllegalStateException("Coupon usage cannot move from " + usage.state() + " to " + to);
        }
        coupons.transition(usage.id(), to, clock.instant());
    }
}
