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

    public InventoryLot receive(int units) {
        requirePositive(units);
        return new InventoryLot(
                skuCode, Math.addExact(physicalUnits, units), reservedUnits, blocked, expiresOn, minimumShelfLifeDays);
    }

    public InventoryLot reserve(int units) {
        requirePositive(units);
        if (units > freeUnits()) {
            throw new IllegalArgumentException("Insufficient free inventory");
        }
        return new InventoryLot(
                skuCode, physicalUnits, Math.addExact(reservedUnits, units), blocked, expiresOn, minimumShelfLifeDays);
    }

    public InventoryLot release(int units) {
        requirePositive(units);
        if (units > reservedUnits) {
            throw new IllegalArgumentException("Cannot release more than reserved inventory");
        }
        return new InventoryLot(
                skuCode, physicalUnits, reservedUnits - units, blocked, expiresOn, minimumShelfLifeDays);
    }

    public InventoryLot handoff(int units) {
        requirePositive(units);
        if (units > reservedUnits) {
            throw new IllegalArgumentException("Cannot handoff more than reserved inventory");
        }
        return new InventoryLot(
                skuCode, physicalUnits - units, reservedUnits - units, blocked, expiresOn, minimumShelfLifeDays);
    }

    private static void requirePositive(int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("Units must be positive");
        }
    }
}
