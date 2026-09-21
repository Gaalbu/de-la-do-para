package br.com.deladopara.inventory.adapter.web.dto;

import java.time.LocalDate;
import java.util.UUID;

public record InventoryLotResponse(
        UUID id,
        String skuCode,
        int physicalUnits,
        int reservedUnits,
        int freeUnits,
        boolean blocked,
        LocalDate expiresOn,
        Integer minimumShelfLifeDays,
        int version) {}
