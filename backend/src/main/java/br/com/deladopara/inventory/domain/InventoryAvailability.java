package br.com.deladopara.inventory.domain;

import java.time.LocalDate;
import java.util.List;

public final class InventoryAvailability {

    private InventoryAvailability() {}

    public static int availableFor(List<InventoryLot> lots, String skuCode, LocalDate arrivalDate) {
        if (lots == null || skuCode == null || skuCode.isBlank() || arrivalDate == null) {
            throw new IllegalArgumentException("Availability query is incomplete");
        }
        long total = 0;
        for (InventoryLot lot : lots) {
            if (lot == null) {
                throw new IllegalArgumentException("Inventory lot is required");
            }
            if (skuCode.equals(lot.skuCode())) {
                total = Math.addExact(total, lot.availableFor(arrivalDate));
            }
        }
        return Math.toIntExact(total);
    }
}
