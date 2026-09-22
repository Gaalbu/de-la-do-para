package br.com.deladopara.catalog.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.catalog.domain.Product.Category;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductCategoryInvariantTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Test
    void foodSkuRequiresPositivePackageContentAndArrivalShelfLife() {
        var product = product(Category.FOOD, producer(true));

        assertThatThrownBy(() -> sku(product, "Pacote", null, 30, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sku(product, "Pacote", 0, 30, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sku(product, "Pacote", 200, 0, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sku(product, "Pacote", 200, 30, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void craftSkuHasNoFoodShelfLifeAndOnlyActiveProducersCanReceiveNewProducts() {
        var product = product(Category.CRAFT, producer(true));
        sku(product, "Peça", null, null, true);
        assertThatThrownBy(() -> sku(product, "Peça", null, 30, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product(Category.CRAFT, producer(false))).isInstanceOf(IllegalArgumentException.class);
    }

    private static Product product(Category category, Producer producer) {
        return new Product(
                UUID.randomUUID(),
                "demo-item",
                "Item de demonstração",
                "Produto fictício para testes.",
                category,
                producer,
                NOW);
    }

    private static ProductSku sku(
            Product product, String salesUnit, Integer netContentGrams, Integer minimumShelfLifeDays, boolean fragile) {
        return new ProductSku(
                UUID.randomUUID(),
                product,
                "DEMO-ITEM",
                salesUnit,
                netContentGrams,
                minimumShelfLifeDays,
                fragile,
                180,
                120,
                40,
                220,
                NOW);
    }

    private static Producer producer(boolean active) {
        var producer = new Producer(
                UUID.randomUUID(),
                "demo-producer",
                "Produtor de demonstração",
                "Localidade ampla (demonstração)",
                "Texto editorial fictício de demonstração.",
                NOW);
        producer.setActive(active, NOW);
        return producer;
    }
}
