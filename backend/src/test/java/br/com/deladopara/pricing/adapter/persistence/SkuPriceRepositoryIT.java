package br.com.deladopara.pricing.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.catalog.adapter.persistence.ProducerRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import br.com.deladopara.catalog.domain.Producer;
import br.com.deladopara.catalog.domain.Product;
import br.com.deladopara.catalog.domain.ProductSku;
import br.com.deladopara.pricing.domain.Money;
import br.com.deladopara.support.PostgresTestContainer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresTestContainer.class)
class SkuPriceRepositoryIT {

    private final SkuPriceRepository prices;
    private final ProductSkuRepository skus;
    private final ProductRepository products;
    private final ProducerRepository producers;

    SkuPriceRepositoryIT(
            SkuPriceRepository prices,
            ProductSkuRepository skus,
            ProductRepository products,
            ProducerRepository producers) {
        this.prices = prices;
        this.skus = skus;
        this.products = products;
        this.producers = producers;
    }

    @Test
    @Transactional
    void persistsAndReadsTheCurrentPriceBySku() {
        var now = Instant.parse("2026-09-22T12:00:00Z");
        var producer = new Producer(
                UUID.randomUUID(),
                "pricing-producer-" + UUID.randomUUID().toString().substring(0, 8),
                "Produtor de demonstração",
                "Localidade ampla (demonstração)",
                "Texto editorial fictício de demonstração.",
                now);
        producers.save(producer);
        var product = new Product(
                UUID.randomUUID(),
                "pricing-product-" + UUID.randomUUID().toString().substring(0, 8),
                "Produto de preço",
                "Registro fictício.",
                Product.Category.FOOD,
                producer,
                now);
        products.save(product);
        var sku = new ProductSku(
                UUID.randomUUID(), product, "PRICING-200G", "Pacote", 200, 30, false, 180, 120, 40, 220, now);
        skus.save(sku);

        prices.save(new SkuPriceEntity(sku.getId(), Money.brl(1_800), now));

        var loaded = prices.findById(sku.getId()).orElseThrow().toDomain();
        assertThat(loaded.skuId()).isEqualTo(sku.getId());
        assertThat(loaded.unitPrice()).isEqualTo(Money.brl(1_800));
    }
}
