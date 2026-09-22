package br.com.deladopara.catalog.adapter.web.dto;

import br.com.deladopara.catalog.domain.Product;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PublicProductResponse(
        String slug,
        String displayName,
        String description,
        Product.Category category,
        boolean demonstration,
        ProductImageResponse image,
        PublicProducer producer,
        List<PublicProductSku> skus) {

    public static PublicProductResponse from(
            Product product, List<ProductSkuResponse> skus, ProductImageResponse image) {
        return from(product, skus, image, Map.of(), Map.of());
    }

    public static PublicProductResponse from(
            Product product,
            List<ProductSkuResponse> skus,
            ProductImageResponse image,
            Map<UUID, Long> prices,
            Map<UUID, Integer> availableUnits) {
        var producer = product.getProducer();
        return new PublicProductResponse(
                product.getSlug(),
                product.getDisplayName(),
                product.getDescription(),
                product.getCategory(),
                true,
                image,
                new PublicProducer(
                        producer.getDisplayName(), producer.getOriginLabel(), producer.getDescription(), true),
                skus.stream()
                        .map(sku -> new PublicProductSku(
                                sku.id(),
                                sku.skuCode(),
                                sku.salesUnit(),
                                sku.netContentGrams(),
                                sku.minimumShelfLifeDays(),
                                sku.fragile(),
                                sku.lengthMm(),
                                sku.widthMm(),
                                sku.heightMm(),
                                sku.grossWeightGrams(),
                                prices.get(sku.id()),
                                availableUnits.getOrDefault(sku.id(), 0)))
                        .toList());
    }

    public record PublicProducer(String displayName, String originLabel, String description, boolean demonstration) {}

    public record PublicProductSku(
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
            Long priceCents,
            int availableUnits) {}
}
