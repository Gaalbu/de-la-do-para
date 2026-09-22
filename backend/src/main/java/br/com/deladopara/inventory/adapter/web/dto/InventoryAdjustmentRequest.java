package br.com.deladopara.inventory.adapter.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record InventoryAdjustmentRequest(
        @NotNull UUID actorId,
        @NotNull @PositiveOrZero Integer physicalUnits,
        @NotNull Integer expectedVersion,
        @NotBlank String reason) {}
