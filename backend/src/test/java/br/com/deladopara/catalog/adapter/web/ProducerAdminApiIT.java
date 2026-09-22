package br.com.deladopara.catalog.adapter.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.deladopara.support.PostgresTestContainer;
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
class ProducerAdminApiIT {

    private final MockMvc mvc;

    @Autowired
    ProducerAdminApiIT(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void requiresAdminRoleAndReturnsCorrelatedProblemDetails() throws Exception {
        mvc.perform(get("/api/v1/admin/producers"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("IDENTITY_006"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        mvc.perform(get("/api/v1/admin/producers").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("IDENTITY_008"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void createsReadsUpdatesDeactivatesAndListsProducers() throws Exception {
        var created = mvc.perform(post("/api/v1/admin/producers")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"test-producer","displayName":"Produtor de demonstração",
                                 "originLabel":"Localidade ampla fictícia — demonstração",
                                 "description":"Texto editorial fictício para demonstração."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(
                        header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/admin/producers/")))
                .andExpect(jsonPath("$.demonstration").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        var id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/v1/admin/producers/" + id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("test-producer"));

        mvc.perform(patch("/api/v1/admin/producers/" + id)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"test-producer","displayName":"Produtor atualizado",
                                 "originLabel":"Outra localidade ampla fictícia — demonstração",
                                 "description":"Texto atualizado, identificado como fictício.","active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.demonstration").value(true))
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/v1/admin/producers?page=0&size=50")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem(id)));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/v1/admin/producers/" + id)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void reportsValidationDuplicateMissingProducerAndInvalidPagination() throws Exception {
        mvc.perform(post("/api/v1/admin/producers")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"invalid slug","displayName":"x","originLabel":"y","description":"z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CATALOG_001"));

        mvc.perform(patch("/api/v1/admin/producers/00000000-0000-0000-0000-000000000001")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"producer","displayName":"Produtor de demonstração",
                                 "originLabel":"Localidade ampla fictícia — demonstração",
                                 "description":"Texto editorial fictício para demonstração."}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CATALOG_001"));

        mvc.perform(get("/api/v1/admin/producers/not-a-uuid").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/admin/producers/00000000-0000-0000-0000-000000000001")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CATALOG_003"));

        mvc.perform(get("/api/v1/admin/producers?size=51").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CATALOG_001"));
    }

    @Test
    void requiresCsrfForWritesAndReportsDuplicateSlugs() throws Exception {
        var body = """
                {"slug":"duplicate-test-producer","displayName":"Produtor de demonstração",
                 "originLabel":"Localidade ampla fictícia — demonstração",
                 "description":"Texto editorial fictício para demonstração."}
                """;
        mvc.perform(post("/api/v1/admin/producers")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/admin/producers")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/admin/producers")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CATALOG_002"));
    }
}
