package br.com.deladopara.catalog.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.deladopara.catalog.adapter.persistence.ProducerRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductRepository;
import br.com.deladopara.catalog.domain.Producer;
import br.com.deladopara.support.PostgresTestContainer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class ProductAdminApiIT {

    private final MockMvc mvc;
    private final ProducerRepository producers;
    private final ProductRepository products;

    @Autowired
    ProductAdminApiIT(MockMvc mvc, ProducerRepository producers, ProductRepository products) {
        this.mvc = mvc;
        this.producers = producers;
        this.products = products;
    }

    @Test
    void createsProductWithVariantsEditsAndDeactivatesWithoutRemovingReferences() throws Exception {
        var producer = producer();
        var created = mvc.perform(post("/api/v1/admin/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("demo-food", producer.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/admin/products/")))
                .andExpect(jsonPath("$.demonstration").value(true))
                .andExpect(jsonPath("$.skus.length()").value(2))
                .andReturn();
        String productId =
                com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        String skuId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.skus[0].id");

        mvc.perform(get("/api/v1/admin/products/" + productId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.producerId").value(producer.getId().toString()));

        mvc.perform(get("/api/v1/products/demo-food"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demonstration").value(true))
                .andExpect(jsonPath("$.producer.originLabel").value("Localidade ampla (demonstração)"))
                .andExpect(jsonPath("$.producer.description").value("Texto editorial fictício de demonstração."))
                .andExpect(jsonPath("$.producer.address").doesNotExist())
                .andExpect(jsonPath("$.producer.coordinates").doesNotExist())
                .andExpect(jsonPath("$.skus.length()").value(2));

        producer.setActive(false, Instant.parse("2026-09-21T12:01:00Z"));
        producers.save(producer);
        mvc.perform(get("/api/v1/products/demo-food")).andExpect(status().isNotFound());
        producer.setActive(true, Instant.parse("2026-09-21T12:02:00Z"));
        producers.save(producer);

        mvc.perform(patch("/api/v1/admin/products/" + productId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productUpdateBody("demo-food", producer.getId(), skuId, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.skus[0].id").value(skuId))
                .andExpect(jsonPath("$.skus[0].active").value(false))
                .andExpect(jsonPath("$.skus[1].active").value(false));

        mvc.perform(get("/api/v1/products/demo-food")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/admin/products/" + productId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productUpdateBody("demo-food", producer.getId(), skuId, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/products/demo-food")).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/v1/admin/products/" + productId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/v1/admin/products?page=0&size=50")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem(productId)))
                .andExpect(jsonPath("$.content[?(@.id=='" + productId + "')].active")
                        .value(hasItem(false)));
    }

    @Test
    void protectsAdminRoutesAndRequiresCsrfForWrites() throws Exception {
        mvc.perform(get("/api/v1/admin/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
        mvc.perform(get("/api/v1/admin/products").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/products?size=51").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CATALOG_001"));
        mvc.perform(get("/api/v1/admin/products/00000000-0000-0000-0000-000000000001")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CATALOG_003"));
        mvc.perform(post("/api/v1/admin/products")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publishesCraftSkuWithoutFoodContentOrShelfLife() throws Exception {
        var producer = producer();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var slug = "demo-craft-" + suffix;
        mvc.perform(post("/api/v1/admin/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","displayName":"Cuia de demonstração","description":"Peça fictícia de demonstração.",
                                 "category":"CRAFT","producerId":"%s","skus":[
                                   {"skuCode":"CRAFT-POT-%s","salesUnit":"Peça","fragile":true,
                                    "lengthMm":120,"widthMm":130,"heightMm":150,"grossWeightGrams":300,"active":true}]}
                                """.formatted(slug, producer.getId(), suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.skus[0].netContentGrams").value(nullValue()))
                .andExpect(jsonPath("$.skus[0].minimumShelfLifeDays").value(nullValue()));

        mvc.perform(get("/api/v1/products/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("CRAFT"))
                .andExpect(jsonPath("$.skus[0].fragile").value(true))
                .andExpect(jsonPath("$.skus[0].netContentGrams").value(nullValue()))
                .andExpect(jsonPath("$.skus[0].minimumShelfLifeDays").value(nullValue()));
    }

    @Test
    void rejectsInvalidProductAndDuplicateSkuWithProblemDetails() throws Exception {
        var producer = producer();
        mvc.perform(post("/api/v1/admin/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("invalid-product", producer.getId())
                                .replace("\"lengthMm\":180", "\"lengthMm\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CATALOG_001"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
        assertThat(products.existsBySlug("invalid-product")).isFalse();

        var body = productBody("first-product", producer.getId());
        mvc.perform(post("/api/v1/admin/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/admin/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("second-product", producer.getId())
                                .replace("SECOND-PRODUCT-200G", "FIRST-PRODUCT-200G")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CATALOG_002"));
        assertThat(products.existsBySlug("second-product")).isFalse();
    }

    private Producer producer() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        return producers.save(new Producer(
                UUID.randomUUID(),
                "product-api-producer-" + suffix,
                "Produtor de demonstração",
                "Localidade ampla (demonstração)",
                "Texto editorial fictício de demonstração.",
                Instant.parse("2026-09-21T12:00:00Z")));
    }

    private static String productBody(String slug, UUID producerId) {
        return """
                {"slug":"%s","displayName":"Alimento de demonstração","description":"Produto fictício de demonstração.",
                 "category":"FOOD","producerId":"%s","skus":[
                   {"skuCode":"%s-200G","salesUnit":"Pacote","netContentGrams":200,"minimumShelfLifeDays":30,
                    "fragile":false,"lengthMm":180,"widthMm":120,"heightMm":40,"grossWeightGrams":220,"active":true},
                   {"skuCode":"%s-500G","salesUnit":"Pacote","netContentGrams":500,"minimumShelfLifeDays":30,
                    "fragile":false,"lengthMm":200,"widthMm":140,"heightMm":50,"grossWeightGrams":520,"active":true}]}
                """.formatted(
                slug, producerId, slug.toUpperCase(java.util.Locale.ROOT), slug.toUpperCase(java.util.Locale.ROOT));
    }

    private static String productUpdateBody(String slug, UUID producerId, String skuId, boolean active) {
        return """
                {"slug":"%s","displayName":"Alimento de demonstração atualizado","description":"Produto fictício de demonstração.",
                 "category":"FOOD","producerId":"%s","active":%s,"skus":[
                   {"id":"%s","skuCode":"%s-200G","salesUnit":"Pacote","netContentGrams":200,"minimumShelfLifeDays":30,
                    "fragile":false,"lengthMm":180,"widthMm":120,"heightMm":40,"grossWeightGrams":220,"active":false}]}
                """.formatted(slug, producerId, active, skuId, slug.toUpperCase(java.util.Locale.ROOT));
    }
}
