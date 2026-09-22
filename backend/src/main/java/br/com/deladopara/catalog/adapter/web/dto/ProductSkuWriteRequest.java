package br.com.deladopara.catalog.adapter.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ProductSkuWriteRequest(
        UUID id,

        @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*")
        String skuCode,

        @NotBlank @Size(max = 40) String salesUnit,
        @Positive Integer netContentGrams,
        @Positive Integer minimumShelfLifeDays,
        @NotNull Boolean fragile,
        @NotNull @Positive Integer lengthMm,
        @NotNull @Positive Integer widthMm,
        @NotNull @Positive Integer heightMm,
        @NotNull @Positive Integer grossWeightGrams,
        @NotNull Boolean active,
        Boolean pickupEligible) {}
