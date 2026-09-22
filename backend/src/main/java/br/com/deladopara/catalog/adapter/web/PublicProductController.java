package br.com.deladopara.catalog.adapter.web;

import br.com.deladopara.catalog.adapter.web.dto.PublicProductResponse;
import br.com.deladopara.catalog.application.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
