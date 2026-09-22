package br.com.deladopara.catalog.adapter.web;

import br.com.deladopara.catalog.adapter.web.dto.ProductPageResponse;
import br.com.deladopara.catalog.adapter.web.dto.ProductResponse;
import br.com.deladopara.catalog.adapter.web.dto.ProductUpdateRequest;
import br.com.deladopara.catalog.adapter.web.dto.ProductWriteRequest;
import br.com.deladopara.catalog.application.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/products")
public class ProductAdminController {

    private final ProductService products;

    public ProductAdminController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    public ProductPageResponse list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return products.list(page, size);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductWriteRequest request) {
        var response = products.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/products/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return products.get(id);
    }

    @PatchMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request) {
        return products.update(id, request);
    }
}
