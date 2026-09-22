package br.com.deladopara.catalog.adapter.web.dto;

import br.com.deladopara.catalog.domain.ProductSku;
import java.time.Instant;
import java.util.UUID;

public record ProductSkuResponse(
        UUID id,
        String skuCode,
        String salesUnit,
        Integer netContentGrams,
        Integer minimumShelfLifeDays,
        boolean fragile,
        int lengthMm,
        int widthMm,
        int heightMm,
        int grossWeightGrams,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductSkuResponse from(ProductSku sku) {
        return new ProductSkuResponse(
                sku.getId(),
                sku.getSkuCode(),
                sku.getSalesUnit(),
                sku.getNetContentGrams(),
                sku.getMinimumShelfLifeDays(),
                sku.isFragile(),
                sku.getLengthMm(),
                sku.getWidthMm(),
                sku.getHeightMm(),
                sku.getGrossWeightGrams(),
                sku.isActive(),
                sku.getCreatedAt(),
                sku.getUpdatedAt());
    }
}
