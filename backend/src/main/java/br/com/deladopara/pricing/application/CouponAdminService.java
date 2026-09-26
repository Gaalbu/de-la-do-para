package br.com.deladopara.pricing.application;

import br.com.deladopara.pricing.adapter.persistence.CouponAdminRepository;
import br.com.deladopara.pricing.adapter.web.dto.CouponPageResponse;
import br.com.deladopara.pricing.adapter.web.dto.CouponResponse;
import br.com.deladopara.pricing.adapter.web.dto.CouponUpdateRequest;
import br.com.deladopara.pricing.adapter.web.dto.CouponWriteRequest;
import br.com.deladopara.pricing.domain.CouponDiscount;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coupon administration. Coupons are never deleted: deactivation keeps the row so usage history stays intact.
 * Reads never touch usage.
 */
@Service
public class CouponAdminService {

    private final CouponAdminRepository coupons;
    private final Clock clock;

    public CouponAdminService(CouponAdminRepository coupons, Clock clock) {
        this.coupons = coupons;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CouponPageResponse list(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new InvalidCouponInputException("página deve ser >= 0 e tamanho entre 1 e 50");
        }
        var total = coupons.count();
        return new CouponPageResponse(
                coupons.page(page, size), page, size, total, (int) Math.ceil(total / (double) size));
    }

    @Transactional(readOnly = true)
    public CouponResponse get(UUID id) {
        return coupons.find(id).orElseThrow(CouponNotFoundException::new);
    }

    @Transactional
    public CouponResponse create(CouponWriteRequest request) {
        var discount = discount(request.discountType(), request.discountValue(), request.minimumCents());
        validateWindow(request.validFrom(), request.validUntil());
        var code = CouponReservationService.normalizeCode(request.code());
        if (coupons.existsByCode(code)) {
            throw new CouponCodeConflictException();
        }
        var id = UUID.randomUUID();
        try {
            coupons.insert(
                    id,
                    code,
                    discount,
                    request.validFrom(),
                    request.validUntil(),
                    request.globalLimit(),
                    request.perEmailLimit(),
                    clock.instant());
        } catch (DuplicateKeyException concurrentCreate) {
            throw new CouponCodeConflictException();
        }
        return coupons.find(id).orElseThrow();
    }

    @Transactional
    public CouponResponse update(UUID id, CouponUpdateRequest request) {
        var current = coupons.findForUpdate(id).orElseThrow(CouponNotFoundException::new);
        var discount = discount(request.discountType(), request.discountValue(), request.minimumCents());
        validateWindow(request.validFrom(), request.validUntil());
        if (request.globalLimit() != null && request.globalLimit() < current.globalUsage()) {
            throw new CouponLimitBelowUsageException();
        }
        coupons.update(
                id,
                discount,
                request.validFrom(),
                request.validUntil(),
                request.globalLimit(),
                request.perEmailLimit(),
                Boolean.TRUE.equals(request.active()),
                clock.instant());
        return coupons.find(id).orElseThrow();
    }

    private static CouponDiscount discount(CouponDiscount.Type type, long value, long minimumCents) {
        if (type == CouponDiscount.Type.PERCENTAGE && value > 100) {
            throw new InvalidCouponInputException("desconto percentual deve estar entre 1 e 100");
        }
        return new CouponDiscount(type, value, minimumCents);
    }

    private static void validateWindow(Instant from, Instant until) {
        if (!until.isAfter(from)) {
            throw new InvalidCouponInputException("fim da validade deve ser posterior ao início");
        }
    }

    public static class InvalidCouponInputException extends RuntimeException {
        public InvalidCouponInputException(String message) {
            super(message);
        }
    }

    public static class CouponNotFoundException extends RuntimeException {}

    public static class CouponCodeConflictException extends RuntimeException {}

    public static class CouponLimitBelowUsageException extends RuntimeException {}
}
