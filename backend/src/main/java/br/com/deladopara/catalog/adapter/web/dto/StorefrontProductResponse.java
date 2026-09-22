package br.com.deladopara.catalog.adapter.web.dto;

import br.com.deladopara.catalog.domain.Product;
import java.util.List;

public record StorefrontProductResponse(
        String slug, String displayName, Product.Category category, PublicProducer producer, List<StorefrontSku> skus) {

    public record PublicProducer(String displayName, String originLabel) {}

    public record StorefrontSku(String skuCode, String salesUnit, long priceCents, int availableUnits) {}
}
