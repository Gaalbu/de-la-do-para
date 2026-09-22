package br.com.deladopara.cart.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.catalog.adapter.persistence.ProducerRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresTestContainer.class)
class CartRepositoryIT {

    private final CartRepository carts;
    private final ProductRepository products;
    private final ProductSkuRepository skus;
    private final ProducerRepository producers;

    @Autowired
    CartRepositoryIT(
            CartRepository carts, ProductRepository products, ProductSkuRepository skus, ProducerRepository producers) {
        this.carts = carts;
        this.products = products;
        this.skus = skus;
        this.producers = producers;
    }

    @Test
    @Transactional
    void persistsGuestCartItemsAndVersionColumn() {
        var now = Instant.parse("2026-09-22T12:00:00Z");
        var producer = new Producer(
                UUID.randomUUID(),
                "cart-producer-" + UUID.randomUUID().toString().substring(0, 8),
                "Produtor de carrinho",
                "Belém (demonstração)",
                "Texto fictício.",
                now);
        var product = new Product(
                UUID.randomUUID(),
                "cart-product-" + UUID.randomUUID().toString().substring(0, 8),
                "Produto de carrinho",
                "Descrição fictícia.",
                Product.Category.CRAFT,
                producer,
                now);
        producers.save(producer);
        products.save(product);
        var sku = new ProductSku(
                UUID.randomUUID(), product, "CART-ITEM", "unidade", null, null, true, 100, 100, 100, 100, now);
        skus.saveAndFlush(sku);

        var cart = new CartEntity(UUID.randomUUID(), "guest-session-hash", null, now);
        cart.getItems().add(new CartItemEntity(cart, sku.getId(), 2, now));
        var saved = carts.saveAndFlush(cart);

        var loaded = carts.findByGuestSessionKey("guest-session-hash").orElseThrow();
        assertThat(saved.getVersion()).isZero();
        assertThat(loaded.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSkuId()).isEqualTo(sku.getId());
            assertThat(item.getQuantity()).isEqualTo(2);
        });
    }
}
