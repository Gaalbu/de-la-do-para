package br.com.deladopara.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductImageInvariantTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Test
    void associatesOnePrimaryImageWithProductAndKeepsOpaqueStorageKey() {
        var product = product();
        var key = UUID.randomUUID();
        var image = new ProductImage(UUID.randomUUID(), product, key, details(), NOW);

        assertThat(image.getProductId()).isEqualTo(product.getId());
        assertThat(image.getStorageKey()).isEqualTo(key);
        assertThat(image.getAltText()).isEqualTo("Cuia decorativa sobre fundo claro");
        assertThat(image.isRightsReviewed()).isTrue();
    }

    @Test
    void rejectsContentTypeAndExtensionThatDoNotMatch() {
        assertThatThrownBy(() -> new ProductImageDetails(
                        "image/png",
                        "jpg",
                        10,
                        10,
                        "Imagem de demonstração",
                        "https://example.test/image",
                        "Licença livre",
                        "Autoria fictícia",
                        "Atribuição",
                        true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresAltTextAndRecordedRightsReview() {
        assertThatThrownBy(() -> new ProductImageDetails(
                        "image/png",
                        "png",
                        10,
                        10,
                        " ",
                        "https://example.test/image",
                        "Licença livre",
                        "Autoria fictícia",
                        "Atribuição",
                        true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductImageDetails(
                        "image/png",
                        "png",
                        10,
                        10,
                        "Descrição alternativa",
                        "https://example.test/image",
                        "Licença livre",
                        "Autoria fictícia",
                        "Atribuição",
                        false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDimensionsOutsideTheProcessingLimits() {
        assertThatThrownBy(() -> new ProductImageDetails(
                        "image/png",
                        "png",
                        3600,
                        3600,
                        "Descrição alternativa",
                        "https://example.test/image",
                        "Licença livre",
                        "Autoria fictícia",
                        "Atribuição",
                        true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replacementRotatesStorageKeyWithoutChangingProductAssociation() {
        var product = product();
        var image = new ProductImage(UUID.randomUUID(), product, UUID.randomUUID(), details(), NOW);
        var newKey = UUID.randomUUID();
        var updatedAt = NOW.plusSeconds(30);

        image.replace(newKey, details(), updatedAt);

        assertThat(image.getProductId()).isEqualTo(product.getId());
        assertThat(image.getStorageKey()).isEqualTo(newKey);
        assertThat(image.getUpdatedAt()).isEqualTo(updatedAt);
    }

    private static Product product() {
        var producer = new Producer(
                UUID.randomUUID(),
                "produtor-demo",
                "Produtor de demonstração",
                "Localidade ampla (demonstração)",
                "Narrativa fictícia.",
                NOW);
        return new Product(
                UUID.randomUUID(),
                "produto-demo",
                "Produto de demonstração",
                "Texto fictício.",
                Product.Category.CRAFT,
                producer,
                NOW);
    }

    private static ProductImageDetails details() {
        return new ProductImageDetails(
                "image/png",
                "png",
                320,
                240,
                "Cuia decorativa sobre fundo claro",
                "https://example.test/image",
                "Licença livre",
                "Autoria fictícia",
                "Atribuição",
                true);
    }
}
