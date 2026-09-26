package br.com.deladopara.pricing.adapter.web.dto;

import br.com.deladopara.pricing.domain.CouponDiscount;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Full editable state. The code is immutable so historical usage keeps pointing at what customers typed. */
public record CouponUpdateRequest(
        @NotNull CouponDiscount.Type discountType,
        @Min(1) long discountValue,
        @Min(0) long minimumCents,
        @NotNull Instant validFrom,
        @NotNull Instant validUntil,
        @Min(1) Integer globalLimit,
        @Min(1) @Max(1000) int perEmailLimit,
        @NotNull Boolean active) {}
