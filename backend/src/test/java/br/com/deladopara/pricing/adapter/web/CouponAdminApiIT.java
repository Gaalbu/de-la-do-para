package br.com.deladopara.pricing.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.deladopara.pricing.adapter.web.dto.CouponWriteRequest;
import br.com.deladopara.pricing.application.CouponAdminService;
import br.com.deladopara.pricing.application.CouponAdminService.CouponCodeConflictException;
import br.com.deladopara.pricing.application.CouponReservationService;
import br.com.deladopara.pricing.domain.CouponDiscount;
import br.com.deladopara.support.PostgresTestContainer;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class CouponAdminApiIT {

    private static final String BODY = """
            {"code":"%s","discountType":"PERCENTAGE","discountValue":%d,"minimumCents":2000,
             "validFrom":"2026-01-01T00:00:00Z","validUntil":"2099-12-31T23:59:59Z",
             "globalLimit":%s,"perEmailLimit":1}
            """;

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final CouponReservationService reservations;
    private final CouponAdminService admin;

    @Autowired
    CouponAdminApiIT(MockMvc mvc, JdbcTemplate jdbc, CouponReservationService reservations, CouponAdminService admin) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.reservations = reservations;
        this.admin = admin;
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE coupon_usage, coupon CASCADE");
    }

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private String create(String code, int value, String globalLimit) throws Exception {
        var result = mvc.perform(post("/api/v1/admin/coupons")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(code, value, globalLimit)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void requiresAdminRole() throws Exception {
        mvc.perform(get("/api/v1/admin/coupons")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/coupons").with(user("c").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/coupons")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("SEMCSRF", 10, "null")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsNormalizesReadsListsAndRejectsDuplicateCode() throws Exception {
        var id = create("bemvindo", 10, "50");

        mvc.perform(get("/api/v1/admin/coupons/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BEMVINDO"))
                .andExpect(jsonPath("$.globalLimit").value(50))
                .andExpect(jsonPath("$.globalUsage").value(0))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/v1/admin/coupons?page=0&size=20").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem(id)));

        mvc.perform(post("/api/v1/admin/coupons")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("BemVindo", 5, "null")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PRICING_002"));
    }

    @Test
    void validatesInput() throws Exception {
        mvc.perform(post("/api/v1/admin/coupons")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("PERC", 101, "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PRICING_001"));
        mvc.perform(post("/api/v1/admin/coupons")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("x", 10, "null")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/coupons")
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"JANELA","discountType":"FIXED","discountValue":500,"minimumCents":0,
                                 "validFrom":"2026-12-31T00:00:00Z","validUntil":"2026-01-01T00:00:00Z",
                                 "perEmailLimit":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PRICING_001"));
        mvc.perform(get("/api/v1/admin/coupons/00000000-0000-0000-0000-000000000000")
                        .with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PRICING_003"));
        mvc.perform(get("/api/v1/admin/coupons?size=0").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void updatesDeactivatesAndKeepsHistoryAndUsageUntouchedByReads() throws Exception {
        var id = create("HIST", 10, "5");
        reservations.reserve("hist", "a@x.com", 5_000, "order-hist");

        mvc.perform(get("/api/v1/admin/coupons/" + id).with(admin()))
                .andExpect(jsonPath("$.globalUsage").value(1));
        mvc.perform(get("/api/v1/admin/coupons").with(admin())).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject("SELECT count(*) FROM coupon_usage", Integer.class))
                .isEqualTo(1);

        mvc.perform(patch("/api/v1/admin/coupons/" + id)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"discountType":"FIXED","discountValue":700,"minimumCents":1000,
                                 "validFrom":"2026-01-01T00:00:00Z","validUntil":"2099-12-31T23:59:59Z",
                                 "globalLimit":5,"perEmailLimit":2,"active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("HIST"))
                .andExpect(jsonPath("$.discountType").value("FIXED"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.globalUsage").value(1));
        org.assertj.core.api.Assertions.assertThat(reservations
                        .reserve("hist", "b@x.com", 5_000, "order-2")
                        .reserved())
                .isFalse();
    }

    @Test
    void refusesGlobalLimitBelowRecordedUsage() throws Exception {
        var id = create("LIMITE", 10, "3");
        reservations.reserve("limite", "a@x.com", 5_000, "l1");
        reservations.reserve("limite", "b@x.com", 5_000, "l2");

        mvc.perform(patch("/api/v1/admin/coupons/" + id)
                        .with(admin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"discountType":"PERCENTAGE","discountValue":10,"minimumCents":0,
                                 "validFrom":"2026-01-01T00:00:00Z","validUntil":"2099-12-31T23:59:59Z",
                                 "globalLimit":1,"perEmailLimit":1,"active":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PRICING_004"));
    }

    @Test
    void concurrentCreationOfTheSameCodeYieldsOneCouponAndConflicts() throws Exception {
        var request = new CouponWriteRequest(
                "CORRIDA",
                CouponDiscount.Type.PERCENTAGE,
                10,
                0,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2099-12-31T23:59:59Z"),
                null,
                1);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(6);
        try {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (var i = 0; i < 6; i++) {
                tasks.add(() -> {
                    start.await();
                    try {
                        admin.create(request);
                        return true;
                    } catch (CouponCodeConflictException conflict) {
                        return false;
                    }
                });
            }
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            var created = 0;
            for (var future : futures) {
                try {
                    created += future.get() ? 1 : 0;
                } catch (ExecutionException unexpected) {
                    throw new AssertionError("only a code conflict is acceptable", unexpected.getCause());
                }
            }

            assertThat(created).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM coupon", Integer.class))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
