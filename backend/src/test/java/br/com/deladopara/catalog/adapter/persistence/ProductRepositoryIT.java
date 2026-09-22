package br.com.deladopara.catalog.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.catalog.domain.Producer;
import br.com.deladopara.catalog.domain.Product;
import br.com.deladopara.catalog.domain.ProductSku;
import br.com.deladopara.support.PostgresTestContainer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresTestContainer.class)
class ProductRepositoryIT {

    private final ProductRepository products;
    private final ProductSkuRepository skus;
    private final ProducerRepository producers;
    private final JdbcTemplate jdbc;

    @Autowired
    ProductRepositoryIT(
            ProductRepository products, ProductSkuRepository skus, ProducerRepository producers, JdbcTemplate jdbc) {
        this.products = products;
        this.skus = skus;
        this.producers = producers;
        this.jdbc = jdbc;
    }

    @Test
    @Transactional
    void persistsProducerProductAndSkuAndPreservesReferencesAfterDeactivation() {
        var now = Instant.parse("2026-09-21T12:00:00Z");
        var producer = new Producer(
                UUID.randomUUID(),
                "demo-producer-" + UUID.randomUUID().toString().substring(0, 8),
                "Produtor de demonstração",
                "Localidade ampla (demonstração)",
                "Texto editorial fictício de demonstração.",
                now);
        producers.save(producer);
        var product = foodProduct(producer, now);
        products.save(product);
        var sku = new ProductSku(
                UUID.randomUUID(), product, "DEMO-FOOD-200G", "Pacote", 200, 30, false, 180, 120, 40, 220, now);
        var variant = new ProductSku(
                UUID.randomUUID(), product, "DEMO-FOOD-500G", "Pacote", 500, 30, false, 200, 140, 50, 520, now);
        skus.save(sku);
        skus.save(variant);

        var loaded = products.findBySlug(product.getSlug()).orElseThrow();
        loaded.updateDetails(
                loaded.getSlug(),
                "Alimento de demonstração atualizado",
                "Descrição atualizada.",
                loaded.getCategory(),
                loaded.getProducer(),
                now.plusSeconds(30));
        products.save(loaded);
        loaded.setActive(false, now.plusSeconds(60));
        products.save(loaded);

        var persistedProduct = products.findById(product.getId()).orElseThrow();
        var persistedSku = skus.findBySkuCode(sku.getSkuCode()).orElseThrow();
        assertThat(persistedProduct.isActive()).isFalse();
        assertThat(persistedProduct.getId()).isEqualTo(product.getId());
        assertThat(persistedProduct.getProducer().getId()).isEqualTo(producer.getId());
        assertThat(persistedSku.getId()).isEqualTo(sku.getId());
        assertThat(persistedSku.getProduct().getId()).isEqualTo(product.getId());
        assertThat(persistedSku.getSalesUnit()).isEqualTo("Pacote");
        assertThat(persistedSku.getNetContentGrams()).isEqualTo(200);
        assertThat(persistedSku.getGrossWeightGrams()).isEqualTo(220);
        assertThat(skus.existsBySkuCode(sku.getSkuCode())).isTrue();
        var persistedVariant = skus.findBySkuCode(variant.getSkuCode()).orElseThrow();
        assertThat(persistedVariant.getId()).isEqualTo(variant.getId());
        assertThat(persistedVariant.getProduct().getId()).isEqualTo(product.getId());
        assertThat(persistedVariant.getNetContentGrams()).isEqualTo(500);
        assertThat(persistedVariant.getGrossWeightGrams()).isEqualTo(520);
    }

    @Test
    @Transactional
    void rejectsCaseInsensitiveDuplicateSkuCodes() {
        var now = Instant.parse("2026-09-21T12:00:00Z");
        var producer = new Producer(
                UUID.randomUUID(),
                "demo-producer-" + UUID.randomUUID().toString().substring(0, 8),
                "Produtor de demonstração",
                "Localidade ampla (demonstração)",
                "Texto editorial fictício de demonstração.",
                now);
        producers.save(producer);
        var firstProduct = foodProduct(producer, now);
        var secondProduct = foodProduct(producer, now);
        products.save(firstProduct);
        products.save(secondProduct);
        skus.save(new ProductSku(
                UUID.randomUUID(), firstProduct, "DEMO-200G", "Pacote", 200, 30, false, 180, 120, 40, 220, now));

        assertThatThrownBy(() -> skus.saveAndFlush(new ProductSku(
                        UUID.randomUUID(),
                        secondProduct,
                        "demo-200g",
                        "Pacote",
                        200,
                        30,
                        false,
                        160,
                        100,
                        30,
                        210,
                        now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void rejectsNonPositivePackageMeasurementsInTheDatabase() {
        var now = Instant.parse("2026-09-21T12:00:00Z");
        var producer = new Producer(
                UUID.randomUUID(),
                "demo-producer-" + UUID.randomUUID().toString().substring(0, 8),
                "Produtor de demonstração",
                "Localidade ampla (demonstração)",
                "Texto editorial fictício de demonstração.",
                now);
        producers.save(producer);
        var product = foodProduct(producer, now);
        products.save(product);

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO product_skus (id, product_id, product_category, sku_code, sales_unit, "
                                + "net_content_grams, minimum_shelf_life_days, fragile, length_mm, width_mm, "
                                + "height_mm, gross_weight_grams, created_at, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        product.getId(),
                        Product.Category.FOOD.name(),
                        "INVALID-DIMENSION",
                        "Pacote",
                        200,
                        30,
                        false,
                        0,
                        120,
                        40,
                        220,
                        java.sql.Timestamp.from(now),
                        java.sql.Timestamp.from(now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Product foodProduct(Producer producer, Instant now) {
        return new Product(
                UUID.randomUUID(),
                "demo-food-" + UUID.randomUUID().toString().substring(0, 8),
                "Alimento de demonstração",
                "Registro fictício.",
                Product.Category.FOOD,
                producer,
                now);
    }
}
