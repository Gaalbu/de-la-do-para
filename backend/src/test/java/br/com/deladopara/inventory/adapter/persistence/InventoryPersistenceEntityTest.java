package br.com.deladopara.inventory.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.catalog.domain.Product;
import br.com.deladopara.catalog.domain.ProductSku;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryPersistenceEntityTest {

    @Test
    void rejectsImpossibleLotBalanceBeforePersistence() {
        var sku = sku();

        assertThatThrownBy(() -> new InventoryLotEntity(
                        UUID.randomUUID(),
                        sku,
                        2,
                        3,
                        false,
                        null,
                        null,
                        Instant.parse("2026-09-21T12:00:00Z"),
                        Instant.parse("2026-09-21T12:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Lot balance is invalid");
    }

    @Test
    void rejectsBlankMovementIdempotencyKeyBeforePersistence() {
        assertThatThrownBy(() -> new InventoryMovementEntity(
                        UUID.randomUUID(),
                        sku(),
                        "RECEIPT",
                        2,
                        " ",
                        null,
                        "receiving",
                        Instant.parse("2026-09-21T12:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Movement fields are invalid");
    }

    private static ProductSku sku() {
        var now = Instant.parse("2026-09-21T12:00:00Z");
        var producer = new br.com.deladopara.catalog.domain.Producer(
                UUID.randomUUID(), "producer-" + UUID.randomUUID(), "Producer", "Origin", "Description", now);
        var product = new Product(
                UUID.randomUUID(),
                "product-" + UUID.randomUUID(),
                "Food",
                "Description",
                Product.Category.FOOD,
                producer,
                now);
        return new ProductSku(UUID.randomUUID(), product, "SKU-200", "Pacote", 200, 30, false, 180, 120, 40, 220, now);
    }
}
