package br.com.deladopara.catalog.adapter.web.dto;

import br.com.deladopara.catalog.domain.ProductImage;
import java.util.UUID;

public record ProductImageResponse(UUID id, String url, String altText) {
    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(
                image.getId(), "/api/v1/product-images/" + image.getStorageKey(), image.getAltText());
    }
}
