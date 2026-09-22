package br.com.deladopara.catalog.adapter.web.dto;

import br.com.deladopara.catalog.domain.Product;
import java.util.List;

public record PublicProductResponse(
        String slug,
        String displayName,
        String description,
        Product.Category category,
        boolean demonstration,
        PublicProducer producer,
        List<PublicProductSku> skus) {

    public static PublicProductResponse from(Product product, List<ProductSkuResponse> skus) {
        var producer = product.getProducer();
        return new PublicProductResponse(
                product.getSlug(),
                product.getDisplayName(),
                product.getDescription(),
                product.getCategory(),
                true,
                new PublicProducer(
                        producer.getDisplayName(), producer.getOriginLabel(), producer.getDescription(), true),
                skus.stream()
                        .map(sku -> new PublicProductSku(
                                sku.skuCode(),
                                sku.salesUnit(),
                                sku.netContentGrams(),
                                sku.minimumShelfLifeDays(),
                                sku.fragile(),
                                sku.lengthMm(),
                                sku.widthMm(),
                                sku.heightMm(),
                                sku.grossWeightGrams()))
                        .toList());
    }

    public record PublicProducer(String displayName, String originLabel, String description, boolean demonstration) {}

    public record PublicProductSku(
            String skuCode,
            String salesUnit,
            Integer netContentGrams,
            Integer minimumShelfLifeDays,
            boolean fragile,
            int lengthMm,
            int widthMm,
            int heightMm,
            int grossWeightGrams) {}
}
