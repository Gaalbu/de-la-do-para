package br.com.deladopara.pricing.adapter.web.dto;

import br.com.deladopara.pricing.domain.CouponDiscount;
import java.time.Instant;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        String code,
        CouponDiscount.Type discountType,
        long discountValue,
        long minimumCents,
        Instant validFrom,
        Instant validUntil,
        boolean active,
        Integer globalLimit,
        int perEmailLimit,
        int globalUsage,
        Instant createdAt,
        Instant updatedAt) {}
