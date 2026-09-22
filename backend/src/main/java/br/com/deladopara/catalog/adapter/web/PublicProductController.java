package br.com.deladopara.catalog.adapter.web;

import br.com.deladopara.catalog.adapter.web.dto.PublicProductResponse;
import br.com.deladopara.catalog.adapter.web.dto.StorefrontProductPageResponse;
import br.com.deladopara.catalog.application.ProductService;
import br.com.deladopara.catalog.application.StorefrontQuery;
import br.com.deladopara.catalog.domain.Product;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class PublicProductController {

    private final ProductService products;

    public PublicProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping("/{slug}")
    public PublicProductResponse get(@PathVariable String slug) {
        return products.getPublic(slug);
    }

    @GetMapping
    public StorefrontProductPageResponse list(
            @RequestParam(required = false) String producer,
            @RequestParam(required = false) Product.Category category,
            @RequestParam(required = false) Long minPriceCents,
            @RequestParam(required = false) Long maxPriceCents,
            @RequestParam(defaultValue = "RELEVANCE") StorefrontQuery.Sort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return products.findStorefrontProducts(
                new StorefrontQuery(producer, category, minPriceCents, maxPriceCents, sort, page, size));
    }
}
