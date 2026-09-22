package br.com.deladopara.inventory.adapter.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;

public record InventoryLotCreateRequest(
        @NotNull Integer physicalUnits,
        LocalDate expiresOn,
        Integer minimumShelfLifeDays,
        @NotNull Instant receivedAt) {

    public InventoryLotCreateRequest {
        if (physicalUnits == null || physicalUnits <= 0) {
            throw new IllegalArgumentException("physicalUnits must be positive");
        }
        if (expiresOn == null && minimumShelfLifeDays != null || expiresOn != null && minimumShelfLifeDays == null) {
            throw new IllegalArgumentException("Validity fields must be paired");
        }
        if (minimumShelfLifeDays != null && minimumShelfLifeDays < 0) {
            throw new IllegalArgumentException("minimumShelfLifeDays must not be negative");
        }
    }
}
