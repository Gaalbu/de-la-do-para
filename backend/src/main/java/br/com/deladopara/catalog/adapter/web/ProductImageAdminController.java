package br.com.deladopara.catalog.adapter.web;

import br.com.deladopara.catalog.adapter.web.dto.ProductImageResponse;
import br.com.deladopara.catalog.application.ProductImageService;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/products/{productId}/image")
public class ProductImageAdminController {

    private final ProductImageService images;

    public ProductImageAdminController(ProductImageService images) {
        this.images = images;
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageResponse> upload(
            @PathVariable UUID productId,
            @RequestParam("file") MultipartFile file,
            @RequestParam String altText,
            @RequestParam String source,
            @RequestParam String license,
            @RequestParam(defaultValue = "") String creator,
            @RequestParam(defaultValue = "") String attribution,
            @RequestParam boolean rightsReviewed)
            throws IOException {
        var result = images.upload(
                productId, file.getBytes(), altText, source, license, creator, attribution, rightsReviewed);
        var builder = ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK);
        if (result.created()) {
            builder.location(URI.create(result.image().url()));
        }
        return builder.body(result.image());
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(@PathVariable UUID productId) {
        images.remove(productId);
        return ResponseEntity.noContent().build();
    }
}
