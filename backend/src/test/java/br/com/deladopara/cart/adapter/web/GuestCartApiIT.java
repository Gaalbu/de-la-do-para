package br.com.deladopara.cart.adapter.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.deladopara.catalog.adapter.persistence.ProducerRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import br.com.deladopara.catalog.domain.Producer;
import br.com.deladopara.catalog.domain.Product;
import br.com.deladopara.catalog.domain.ProductSku;
import br.com.deladopara.support.PostgresTestContainer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class GuestCartApiIT {

    private final MockMvc mvc;
    private final ProductSkuRepository skus;
    private UUID skuId;

    @Autowired
    GuestCartApiIT(MockMvc mvc, ProductSkuRepository skus, ProductRepository products, ProducerRepository producers) {
        this.mvc = mvc;
        this.skus = skus;
        var now = Instant.parse("2026-09-22T12:00:00Z");
        var producer = new Producer(
                UUID.randomUUID(),
                "api-cart-producer-" + UUID.randomUUID().toString().substring(0, 8),
                "Produtor de carrinho",
                "Belém (demonstração)",
                "Texto fictício.",
                now);
        producers.save(producer);
        var product = new Product(
                UUID.randomUUID(),
                "api-cart-product-" + UUID.randomUUID().toString().substring(0, 8),
                "Produto de carrinho",
                "Descrição fictícia.",
                Product.Category.CRAFT,
                producer,
                now);
        products.save(product);
        skuId = UUID.randomUUID();
        skus.saveAndFlush(new ProductSku(
                skuId,
                product,
                "API-CART-ITEM-" + skuId.toString().substring(0, 8),
                "unidade",
                null,
                null,
                true,
                100,
                100,
                100,
                100,
                now));
    }

    @BeforeEach
    void verifyFixture() {
        skus.findById(skuId).orElseThrow();
    }

    @Test
    void preservesGuestSessionAndRejectsStaleMutation() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(get("/api/v1/cart").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.items").isEmpty());

        mvc.perform(put("/api/v1/cart/items")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"items\":[{\"skuId\":\"" + skuId + "\",\"quantity\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        mvc.perform(put("/api/v1/cart/items")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"items\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CART_002"));

        mvc.perform(delete("/api/v1/cart/items/" + skuId)
                        .session(session)
                        .with(csrf())
                        .param("expectedVersion", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.items").isEmpty());
    }
}
