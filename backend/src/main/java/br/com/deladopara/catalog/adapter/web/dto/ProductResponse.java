package br.com.deladopara.catalog.adapter.web.dto;

import br.com.deladopara.catalog.domain.Product;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String slug,
        String displayName,
        String description,
        Product.Category category,
        UUID producerId,
        boolean demonstration,
        boolean active,
        ProductImageResponse image,
        List<ProductSkuResponse> skus,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(Product product, List<ProductSkuResponse> skus, ProductImageResponse image) {
        return new ProductResponse(
                product.getId(),
                product.getSlug(),
                product.getDisplayName(),
                product.getDescription(),
                product.getCategory(),
                product.getProducer().getId(),
                true,
                product.isActive(),
                image,
                List.copyOf(skus),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
