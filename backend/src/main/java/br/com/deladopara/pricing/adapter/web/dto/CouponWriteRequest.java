package br.com.deladopara.pricing.adapter.web.dto;

import br.com.deladopara.pricing.domain.CouponDiscount;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

public record CouponWriteRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,32}") String code,
        @NotNull CouponDiscount.Type discountType,
        @Min(1) long discountValue,
        @Min(0) long minimumCents,
        @NotNull Instant validFrom,
        @NotNull Instant validUntil,
        @Min(1) Integer globalLimit,
        @Min(1) @Max(1000) int perEmailLimit) {}
