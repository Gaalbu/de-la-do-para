package br.com.deladopara.inventory.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record InventoryLot(
        String skuCode,
        int physicalUnits,
        int reservedUnits,
        boolean blocked,
        LocalDate expiresOn,
        Integer minimumShelfLifeDays) {

    public InventoryLot {
        if (skuCode == null || skuCode.isBlank()) {
            throw new IllegalArgumentException("SKU is required");
        }
        if (physicalUnits < 0 || reservedUnits < 0 || reservedUnits > physicalUnits) {
            throw new IllegalArgumentException("Invalid inventory balance");
        }
        if ((expiresOn == null) != (minimumShelfLifeDays == null)
                || (minimumShelfLifeDays != null && minimumShelfLifeDays < 0)) {
            throw new IllegalArgumentException("Invalid food validity configuration");
        }
    }

    public int freeUnits() {
        return physicalUnits - reservedUnits;
    }

    public int availableFor(LocalDate arrivalDate) {
        if (arrivalDate == null) {
            throw new IllegalArgumentException("Arrival date is required");
        }
        if (blocked || expiresOn != null && ChronoUnit.DAYS.between(arrivalDate, expiresOn) < minimumShelfLifeDays) {
            return 0;
        }
        return freeUnits();
    }
}
