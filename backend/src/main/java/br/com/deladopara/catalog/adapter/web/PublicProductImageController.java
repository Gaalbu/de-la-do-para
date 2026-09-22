package br.com.deladopara.catalog.adapter.web;

import br.com.deladopara.catalog.application.ProductImageService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/product-images")
public class PublicProductImageController {

    private final ProductImageService images;

    public PublicProductImageController(ProductImageService images) {
        this.images = images;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> get(@PathVariable UUID id) {
        var image = images.getPublic(id);
        return ResponseEntity.ok()
                .contentType(image.contentType())
                .cacheControl(CacheControl.noCache())
                .body(image.bytes());
    }
}
