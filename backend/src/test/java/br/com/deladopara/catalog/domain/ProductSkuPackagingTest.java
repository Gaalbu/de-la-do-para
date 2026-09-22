package br.com.deladopara.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductSkuPackagingTest {

    @Test
    void rejectsMissingSkuIdentityAndNonPositiveGrossPackageMeasurements() {
        var now = Instant.parse("2026-09-21T12:00:00Z");
        var product = new Product(
                UUID.randomUUID(),
                "demo-food",
                "Alimento de demonstração",
                "Registro fictício.",
                Product.Category.FOOD,
                producer(now),
                now);

        assertThatThrownBy(() -> sku(product, " ", 180, 120, 40, 220, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sku(product, "DEMO-200G", 0, 120, 40, 220, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sku(product, "DEMO-200G", 180, 120, 40, 0, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsPickupEligibilityAsExplicitSkuData() {
        var now = Instant.parse("2026-09-21T12:00:00Z");
        var product = new Product(
                UUID.randomUUID(),
                "demo-food",
                "Alimento de demonstração",
                "Registro fictício.",
                Product.Category.FOOD,
                producer(now),
                now);
        var sku = new ProductSku(
                UUID.randomUUID(), product, "DEMO-200G", "Pacote", 200, 30, false, 180, 120, 40, 220, true, now);

        assertThat(sku.isPickupEligible()).isTrue();
    }

    private static ProductSku sku(
            Product product, String code, int lengthMm, int widthMm, int heightMm, int weightGrams, Instant now) {
        return new ProductSku(
                UUID.randomUUID(),
                product,
                code,
                "Pacote",
                200,
                30,
                false,
                lengthMm,
                widthMm,
                heightMm,
                weightGrams,
                now);
    }

    private static Producer producer(Instant now) {
        return new Producer(
                UUID.randomUUID(),
                "demo-producer",
                "Produtor de demonstração",
                "Localidade ampla (demonstração)",
                "Texto editorial fictício de demonstração.",
                now);
    }
}
