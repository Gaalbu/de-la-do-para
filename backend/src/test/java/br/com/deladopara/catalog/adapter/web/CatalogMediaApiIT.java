package br.com.deladopara.catalog.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.deladopara.catalog.adapter.persistence.ProducerRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductImageRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import br.com.deladopara.catalog.adapter.storage.LocalImageFileStore;
import br.com.deladopara.catalog.domain.Producer;
import br.com.deladopara.catalog.domain.Product;
import br.com.deladopara.catalog.domain.ProductSku;
import br.com.deladopara.support.PostgresTestContainer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class CatalogMediaApiIT {

    private static final Path MEDIA_DIRECTORY =
            Path.of("target/catalog-media-it").toAbsolutePath();

    @DynamicPropertySource
    static void mediaDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.media.directory", MEDIA_DIRECTORY::toString);
    }

    private final MockMvc mvc;
    private final ProducerRepository producers;
    private final ProductRepository products;
    private final ProductSkuRepository skus;
    private final ProductImageRepository images;
    private final LocalImageFileStore files;

    @Autowired
    CatalogMediaApiIT(
            MockMvc mvc,
            ProducerRepository producers,
            ProductRepository products,
            ProductSkuRepository skus,
            ProductImageRepository images,
            LocalImageFileStore files) {
        this.mvc = mvc;
        this.producers = producers;
        this.products = products;
        this.skus = skus;
        this.images = images;
        this.files = files;
    }

    @BeforeEach
    void clearMediaDirectory() throws Exception {
        Files.createDirectories(MEDIA_DIRECTORY);
        try (var paths = Files.list(MEDIA_DIRECTORY)) {
            for (var path : paths.toList()) Files.deleteIfExists(path);
        }
    }

    @Test
    void uploadsReplacesServesAndRemovesOnePrimaryImageWithPublicVisibilityRules() throws Exception {
        var product = product();
        var first = png(8, 6);
        var create = mvc.perform(multipart("/api/v1/admin/products/{id}/image", product.getId())
                        .file(new MockMultipartFile("file", "ignored.svg", "image/svg+xml", first))
                        .param("altText", "Imagem fictícia do produto")
                        .param("source", "https://example.test/fonte")
                        .param("license", "CC0 de demonstração")
                        .param("creator", "Autoria fictícia")
                        .param("attribution", "Demonstração")
                        .param("rightsReviewed", "true")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.altText").value("Imagem fictícia do produto"))
                .andReturn();
        String firstId = com.jayway.jsonpath.JsonPath.read(create.getResponse().getContentAsString(), "$.id");
        String firstUrl = com.jayway.jsonpath.JsonPath.read(create.getResponse().getContentAsString(), "$.url");
        var firstKey = UUID.fromString(firstUrl.substring(firstUrl.lastIndexOf('/') + 1));
        assertThat(files.read(firstKey, "png")).isNotEmpty();

        mvc.perform(get("/api/v1/admin/products/{id}", product.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image.id").value(firstId));
        mvc.perform(get(firstUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(files.read(firstKey, "png")));
        mvc.perform(get("/api/v1/products/" + product.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image.altText").value("Imagem fictícia do produto"));

        mvc.perform(multipart("/api/v1/admin/products/{id}/image", product.getId())
                        .file(new MockMultipartFile("file", "bad.png", "image/png", "<svg/>".getBytes()))
                        .param("altText", "Substituição inválida")
                        .param("source", "https://example.test/bad")
                        .param("license", "CC0")
                        .param("rightsReviewed", "true")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(get(firstUrl)).andExpect(status().isOk());

        var replacement = mvc.perform(multipart("/api/v1/admin/products/{id}/image", product.getId())
                        .file(new MockMultipartFile("file", "image.png", "image/png", png(10, 10)))
                        .param("altText", "Nova imagem fictícia")
                        .param("source", "https://example.test/outra-fonte")
                        .param("license", "CC0 de demonstração")
                        .param("rightsReviewed", "true")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.altText").value("Nova imagem fictícia"))
                .andReturn();
        String secondUrl =
                com.jayway.jsonpath.JsonPath.read(replacement.getResponse().getContentAsString(), "$.url");
        assertThat(Files.exists(MEDIA_DIRECTORY.resolve(firstKey + ".png"))).isFalse();
        mvc.perform(get(firstUrl)).andExpect(status().isNotFound());
        mvc.perform(get(secondUrl)).andExpect(status().isOk());

        product.setActive(false, Instant.now());
        products.saveAndFlush(product);
        mvc.perform(get(secondUrl)).andExpect(status().isNotFound());
        product.setActive(true, Instant.now());
        products.saveAndFlush(product);
        var sku = skus.findAllByProductIdOrderBySkuCode(product.getId()).getFirst();
        sku.setActive(false, Instant.now());
        skus.saveAndFlush(sku);
        mvc.perform(get(secondUrl)).andExpect(status().isNotFound());
        sku.setActive(true, Instant.now());
        skus.saveAndFlush(sku);

        mvc.perform(delete("/api/v1/admin/products/{id}/image", product.getId())
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(images.findByProduct_Id(product.getId())).isEmpty();
        mvc.perform(get(secondUrl)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsSpoofedFormatsMissingRightsAndUnauthorizedWrites() throws Exception {
        var product = product();
        mvc.perform(multipart("/api/v1/admin/products/{id}/image", product.getId())
                        .file(new MockMultipartFile("file", "x.png", "image/png", "<svg/>".getBytes()))
                        .param("altText", "Descrição")
                        .param("source", "https://example.test/source")
                        .param("license", "CC0")
                        .param("rightsReviewed", "true")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CATALOG_001"));
        mvc.perform(multipart("/api/v1/admin/products/{id}/image", product.getId())
                        .file(new MockMultipartFile("file", "x.png", "image/png", png(4, 4)))
                        .param("altText", "Descrição")
                        .param("source", "https://example.test/source")
                        .param("license", "CC0")
                        .param("rightsReviewed", "false")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/v1/admin/products/{id}/image", product.getId())
                        .file(new MockMultipartFile("file", "x.png", "image/png", png(4, 4)))
                        .param("altText", "Descrição")
                        .param("source", "https://example.test/source")
                        .param("license", "CC0")
                        .param("rightsReviewed", "true")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/admin/products/{id}/image", product.getId())
                        .file(new MockMultipartFile("file", "x.png", "image/png", png(4, 4)))
                        .param("altText", "Descrição")
                        .param("source", "https://example.test/source")
                        .param("license", "CC0")
                        .param("rightsReviewed", "true")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        assertThat(images.findByProduct_Id(product.getId())).isEmpty();

        mvc.perform(multipart("/api/v1/admin/products/{id}/image", product.getId())
                        .file(new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
                        .param("altText", "Descrição")
                        .param("source", "https://example.test/source")
                        .param("license", "CC0")
                        .param("rightsReviewed", "true")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge());
    }

    private Product product() {
        var now = Instant.now();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var producer = producers.save(new Producer(
                UUID.randomUUID(),
                "media-producer-" + suffix,
                "Produtor de demonstração",
                "Localidade ampla (demonstração)",
                "Texto fictício.",
                now));
        var product = new Product(
                UUID.randomUUID(),
                "media-product-" + suffix,
                "Produto de demonstração",
                "Texto fictício.",
                Product.Category.CRAFT,
                producer,
                now);
        products.save(product);
        skus.save(new ProductSku(
                UUID.randomUUID(),
                product,
                "MEDIA-" + suffix.toUpperCase(),
                "Peça",
                null,
                null,
                true,
                10,
                10,
                10,
                100,
                now));
        return product;
    }

    private static byte[] png(int width, int height) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
