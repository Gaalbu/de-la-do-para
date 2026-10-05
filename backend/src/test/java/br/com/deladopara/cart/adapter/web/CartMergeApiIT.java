package br.com.deladopara.cart.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.cart.adapter.persistence.CartEntity;
import br.com.deladopara.cart.adapter.persistence.CartItemEntity;
import br.com.deladopara.cart.adapter.persistence.CartRepository;
import br.com.deladopara.catalog.adapter.persistence.ProducerRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import br.com.deladopara.catalog.domain.Producer;
import br.com.deladopara.catalog.domain.Product;
import br.com.deladopara.catalog.domain.ProductSku;
import br.com.deladopara.identity.application.AccountService;
import br.com.deladopara.identity.domain.Account;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestContainer.class)
class CartMergeApiIT {
    private static final String PASSWORD = "correct-horse-battery";
    private final int port;
    private final CartRepository carts;
    private final AccountService accounts;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final UUID activeSku;
    private final UUID inactiveSku;

    @Autowired
    CartMergeApiIT(
            @Value("${local.server.port}") int port,
            CartRepository carts,
            AccountService accounts,
            JdbcTemplate jdbc,
            ObjectMapper json,
            ProductSkuRepository skus,
            ProductRepository products,
            ProducerRepository producers) {
        this.port = port;
        this.carts = carts;
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.json = json;
        var now = Instant.parse("2026-09-22T12:00:00Z");
        var producer = new Producer(
                UUID.randomUUID(),
                "merge-" + UUID.randomUUID(),
                "Produtor",
                "Belém (demonstração)",
                "Texto fictício.",
                now);
        producers.save(producer);
        var product = new Product(
                UUID.randomUUID(),
                "merge-" + UUID.randomUUID(),
                "Produto",
                "Descrição fictícia.",
                Product.Category.CRAFT,
                producer,
                now);
        products.save(product);
        activeSku = saveSku(skus, product, now, true);
        inactiveSku = saveSku(skus, product, now, false);
    }

    @Test
    void loginRotatesSessionAndAuthenticatedChoiceCombinesBothCarts() throws Exception {
        var email = "merge-" + UUID.randomUUID() + "@example.com";
        var account = accounts.register(email, PASSWORD, Account.Role.CUSTOMER);
        persistCart(null, account.getId(), List.of(new Line(activeSku, 3)));

        var browser = new Browser();
        assertThat(browser.get("/api/v1/cart").statusCode()).isEqualTo(200);
        browser.fetchCsrf();
        var oldSession = browser.cookies.get("DLSESSION");
        var guestCart = browser.put(
                "/api/v1/cart/items",
                "{\"expectedVersion\":0,\"items\":[{\"skuId\":\"" + activeSku + "\",\"quantity\":4}]}");
        assertThat(guestCart.statusCode()).isEqualTo(200);
        var sessionId = sessionIdFromCookie(oldSession);
        var guestKey = br.com.deladopara.cart.application.GuestCartService.hashSession(sessionId);
        assertThat(carts.findByGuestSessionKey(guestKey)).isPresent();
        var now = Instant.now();
        var guestCartId = jdbc.queryForObject("select id from carts where guest_session_key = ?", UUID.class, guestKey);
        jdbc.update(
                "insert into cart_items (cart_id, sku_id, quantity, created_at, updated_at) values (?, ?, ?, ?, ?)",
                guestCartId,
                inactiveSku,
                2,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));

        var login = browser.post("/api/v1/sessions", credentials(email, PASSWORD));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("\"cartMergeRequired\":true");
        assertThat(browser.cookies.get("DLSESSION")).isNotEqualTo(oldSession);

        var replay = new Browser();
        replay.cookies.put("DLSESSION", oldSession);
        assertThat(replay.get("/api/v1/sessions/current").statusCode()).isEqualTo(401);

        browser.fetchCsrf();
        var view = browser.get("/api/v1/cart/merge");
        assertThat(view.statusCode()).isEqualTo(200);
        JsonNode mergeView = json.readTree(view.body());
        assertThat(mergeView.path("accountCart").path("items").size()).isEqualTo(1);
        assertThat(mergeView.path("guestCart").path("items").size()).isEqualTo(2);
        var inactiveSkuIds = new ArrayList<String>();
        mergeView.path("inactiveSkuIds").forEach(value -> inactiveSkuIds.add(value.asText()));
        assertThat(inactiveSkuIds).contains(inactiveSku.toString());

        var accountVersion = mergeView.path("accountCart").path("version").asLong();
        var guestVersion = mergeView.path("guestCart").path("version").asLong();
        var result = browser.post(
                "/api/v1/cart/merge",
                "{\"choice\":\"COMBINE\",\"expectedAccountVersion\":" + accountVersion + ",\"expectedGuestVersion\":"
                        + guestVersion + "}");
        assertThat(result.statusCode()).isEqualTo(200);
        JsonNode merged = json.readTree(result.body());
        assertThat(quantity(merged, activeSku)).isEqualTo(7);
        assertThat(quantity(merged, inactiveSku)).isEqualTo(2);
        assertThat(carts.findByGuestSessionKey(guestKey)).isEmpty();
        assertThat(jdbc.queryForObject(
                        "select count(*) from cart_items ci join carts c on c.id = ci.cart_id where c.account_id = ?",
                        Integer.class,
                        account.getId()))
                .isEqualTo(2);

        assertThat(browser.get("/api/v1/cart/merge").statusCode()).isEqualTo(404);
        assertThat(browser.get("/api/v1/cart").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                        "select count(*) from spring_session where session_id = ?", Integer.class, sessionId))
                .isZero();

        var repeatedLogin = browser.post("/api/v1/sessions", credentials(email, PASSWORD));
        assertThat(repeatedLogin.statusCode()).isEqualTo(200);
        assertThat(repeatedLogin.body()).contains("\"cartMergeRequired\":false");
        assertThat(quantity(json.readTree(browser.get("/api/v1/cart").body()), activeSku))
                .isEqualTo(7);
    }

    @Test
    void staleVersionKeepsBothCartsUnchanged() throws Exception {
        var email = "merge-" + UUID.randomUUID() + "@example.com";
        var account = accounts.register(email, PASSWORD, Account.Role.CUSTOMER);
        persistCart(null, account.getId(), List.of(new Line(activeSku, 3)));

        var browser = new Browser();
        assertThat(browser.get("/api/v1/cart").statusCode()).isEqualTo(200);
        browser.fetchCsrf();
        var oldSession = browser.cookies.get("DLSESSION");
        assertThat(browser.put(
                                "/api/v1/cart/items",
                                "{\"expectedVersion\":0,\"items\":[{\"skuId\":\"" + activeSku + "\",\"quantity\":4}]}")
                        .statusCode())
                .isEqualTo(200);
        var guestKey = br.com.deladopara.cart.application.GuestCartService.hashSession(sessionIdFromCookie(oldSession));
        assertThat(browser.post("/api/v1/sessions", credentials(email, PASSWORD))
                        .statusCode())
                .isEqualTo(200);
        browser.fetchCsrf();

        var response = browser.post("/api/v1/cart/merge", """
                {"choice":"COMBINE","expectedAccountVersion":9,"expectedGuestVersion":1}
                """);
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(carts.findByGuestSessionKey(guestKey)).isPresent();
        assertThat(jdbc.queryForObject(
                        "select ci.quantity from cart_items ci join carts c on c.id = ci.cart_id where c.account_id = ?",
                        Integer.class,
                        account.getId()))
                .isEqualTo(3);
    }

    @Test
    void keepAccountAndReplaceWithGuestAreExplicitChoices() throws Exception {
        var keepEmail = "merge-keep-" + UUID.randomUUID() + "@example.com";
        var keepAccount = accounts.register(keepEmail, PASSWORD, Account.Role.CUSTOMER);
        var keepBrowser = loggedInWithBothCarts(keepEmail, keepAccount, 3, 4);
        var keepKey = keepBrowser.guestCartKey;
        var kept = resolve(keepBrowser, "KEEP_ACCOUNT");
        assertThat(kept.statusCode()).isEqualTo(200);
        assertThat(quantity(json.readTree(kept.body()), activeSku)).isEqualTo(3);
        assertThat(carts.findByGuestSessionKey(keepKey)).isEmpty();

        var replaceEmail = "merge-replace-" + UUID.randomUUID() + "@example.com";
        var replaceAccount = accounts.register(replaceEmail, PASSWORD, Account.Role.CUSTOMER);
        var replaceBrowser = loggedInWithBothCarts(replaceEmail, replaceAccount, 3, 4);
        var replaceKey = replaceBrowser.guestCartKey;
        var replaced = resolve(replaceBrowser, "REPLACE_WITH_GUEST");
        assertThat(replaced.statusCode()).isEqualTo(200);
        assertThat(quantity(json.readTree(replaced.body()), activeSku)).isEqualTo(4);
        assertThat(carts.findByGuestSessionKey(replaceKey)).isEmpty();
    }

    @Test
    void guestOnlyCartMovesToAccountDuringLoginWithoutMergePrompt() throws Exception {
        var email = "merge-guest-only-" + UUID.randomUUID() + "@example.com";
        var account = accounts.register(email, PASSWORD, Account.Role.CUSTOMER);
        var browser = new Browser();
        assertThat(browser.get("/api/v1/cart").statusCode()).isEqualTo(200);
        browser.fetchCsrf();
        var oldSession = browser.cookies.get("DLSESSION");
        assertThat(browser.put(
                                "/api/v1/cart/items",
                                "{\"expectedVersion\":0,\"items\":[{\"skuId\":\"" + activeSku + "\",\"quantity\":4}]}")
                        .statusCode())
                .isEqualTo(200);

        var login = browser.post("/api/v1/sessions", credentials(email, PASSWORD));

        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("\"cartMergeRequired\":false");
        assertThat(browser.cookies.get("DLSESSION")).isNotEqualTo(oldSession);
        assertThat(browser.get("/api/v1/cart").statusCode()).isEqualTo(200);
        assertThat(quantity(json.readTree(browser.get("/api/v1/cart").body()), activeSku))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject(
                        "select count(*) from carts where account_id = ?", Integer.class, account.getId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from carts where guest_session_key is not null", Integer.class))
                .isZero();
    }

    @Test
    void concurrentDuplicateMergeChoiceDoesNotDuplicateQuantities() throws Exception {
        var email = "merge-race-" + UUID.randomUUID() + "@example.com";
        var account = accounts.register(email, PASSWORD, Account.Role.CUSTOMER);
        var browser = loggedInWithBothCarts(email, account, 3, 4);
        var view = json.readTree(browser.get("/api/v1/cart/merge").body());
        var request = "{\"choice\":\"COMBINE\",\"expectedAccountVersion\":"
                + view.path("accountCart").path("version").asLong() + ",\"expectedGuestVersion\":"
                + view.path("guestCart").path("version").asLong() + "}";
        var secondTab = new Browser();
        secondTab.cookies.putAll(browser.cookies);
        secondTab.csrf = browser.csrf;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = CompletableFuture.supplyAsync(() -> sendUnchecked(browser, request), executor);
            var second = CompletableFuture.supplyAsync(() -> sendUnchecked(secondTab, request), executor);
            var statuses = List.of(first.join().statusCode(), second.join().statusCode());
            assertThat(statuses).containsExactlyInAnyOrder(200, 404);
        }

        assertThat(jdbc.queryForObject(
                        "select ci.quantity from cart_items ci join carts c on c.id = ci.cart_id where c.account_id = ? and ci.sku_id = ?",
                        Integer.class,
                        account.getId(),
                        activeSku))
                .isEqualTo(7);
        assertThat(carts.findByGuestSessionKey(browser.guestCartKey)).isEmpty();
    }

    private HttpResponse<String> sendUnchecked(Browser browser, String body) {
        try {
            return browser.post("/api/v1/cart/merge", body);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Test
    void overflowAndCheckoutConflictKeepBothCarts() throws Exception {
        var overflowEmail = "merge-overflow-" + UUID.randomUUID() + "@example.com";
        var overflowAccount = accounts.register(overflowEmail, PASSWORD, Account.Role.CUSTOMER);
        var overflowBrowser = loggedInWithBothCarts(overflowEmail, overflowAccount, 1, 1);
        var overflowGuestKey = overflowBrowser.guestCartKey;
        jdbc.update(
                "update cart_items set quantity = ? where cart_id = (select id from carts where account_id = ?)",
                Integer.MAX_VALUE,
                overflowAccount.getId());
        assertThat(resolve(overflowBrowser, "COMBINE").statusCode()).isEqualTo(409);
        assertThat(carts.findByGuestSessionKey(overflowGuestKey)).isPresent();
        assertThat(jdbc.queryForObject(
                        "select ci.quantity from cart_items ci join carts c on c.id = ci.cart_id where c.account_id = ?",
                        Integer.class,
                        overflowAccount.getId()))
                .isEqualTo(Integer.MAX_VALUE);

        var checkoutEmail = "merge-checkout-" + UUID.randomUUID() + "@example.com";
        var checkoutAccount = accounts.register(checkoutEmail, PASSWORD, Account.Role.CUSTOMER);
        var checkoutBrowser = loggedInWithBothCarts(checkoutEmail, checkoutAccount, 1, 1);
        var checkoutGuestKey = checkoutBrowser.guestCartKey;
        jdbc.update("update carts set status = 'CHECKOUT_STARTED' where account_id = ?", checkoutAccount.getId());
        assertThat(resolve(checkoutBrowser, "KEEP_ACCOUNT").statusCode()).isEqualTo(409);
        assertThat(carts.findByGuestSessionKey(checkoutGuestKey)).isPresent();
        assertThat(jdbc.queryForObject(
                        "select status from carts where account_id = ?", String.class, checkoutAccount.getId()))
                .isEqualTo("CHECKOUT_STARTED");
    }

    private Browser loggedInWithBothCarts(String email, Account account, int accountQuantity, int guestQuantity)
            throws Exception {
        persistCart(null, account.getId(), List.of(new Line(activeSku, accountQuantity)));
        var browser = new Browser();
        assertThat(browser.get("/api/v1/cart").statusCode()).isEqualTo(200);
        browser.fetchCsrf();
        var guestCart = browser.put(
                "/api/v1/cart/items",
                "{\"expectedVersion\":0,\"items\":[{\"skuId\":\"" + activeSku + "\",\"quantity\":" + guestQuantity
                        + "}]}");
        assertThat(guestCart.statusCode()).isEqualTo(200);
        browser.guestCartKey = br.com.deladopara.cart.application.GuestCartService.hashSession(
                sessionIdFromCookie(browser.cookies.get("DLSESSION")));
        var login = browser.post("/api/v1/sessions", credentials(email, PASSWORD));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("\"cartMergeRequired\":true");
        browser.fetchCsrf();
        return browser;
    }

    private HttpResponse<String> resolve(Browser browser, String choice) throws Exception {
        var view = json.readTree(browser.get("/api/v1/cart/merge").body());
        return browser.post(
                "/api/v1/cart/merge",
                "{\"choice\":\"" + choice + "\",\"expectedAccountVersion\":"
                        + view.path("accountCart").path("version").asLong() + ",\"expectedGuestVersion\":"
                        + view.path("guestCart").path("version").asLong() + "}");
    }

    private void persistCart(String guestKey, UUID owner, List<Line> lines) {
        var cart = carts.saveAndFlush(new CartEntity(UUID.randomUUID(), guestKey, owner, Instant.now()));
        cart.replaceItems(
                lines.stream()
                        .map(line -> new CartItemEntity(cart, line.sku(), line.quantity(), Instant.now()))
                        .toList(),
                Instant.now());
        carts.saveAndFlush(cart);
    }

    private static UUID saveSku(ProductSkuRepository skus, Product product, Instant now, boolean active) {
        var id = UUID.randomUUID();
        var sku = new ProductSku(
                id,
                product,
                "MERGE-" + id.toString().substring(0, 8),
                "unidade",
                null,
                null,
                false,
                100,
                100,
                100,
                100,
                now);
        sku.setActive(active, now);
        skus.saveAndFlush(sku);
        return id;
    }

    private static int quantity(JsonNode cart, UUID skuId) {
        for (var item : cart.path("items")) {
            if (skuId.toString().equals(item.path("skuId").asText()))
                return item.path("quantity").asInt();
        }
        return 0;
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String sessionIdFromCookie(String cookieValue) {
        return new String(Base64.getUrlDecoder().decode(cookieValue), java.nio.charset.StandardCharsets.UTF_8);
    }

    private final class Browser {
        private final Map<String, String> cookies = new LinkedHashMap<>();
        private String csrf;
        private String guestCartKey;

        private void fetchCsrf() throws Exception {
            var response = get("/api/v1/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            csrf = json.readTree(response.body()).path("token").asText();
        }

        private HttpResponse<String> get(String path) throws Exception {
            return send("GET", path, null);
        }

        private HttpResponse<String> put(String path, String body) throws Exception {
            return send("PUT", path, body);
        }

        private HttpResponse<String> post(String path, String body) throws Exception {
            return send("POST", path, body);
        }

        private HttpResponse<String> send(String method, String path, String body) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json");
            if (!cookies.isEmpty()) {
                builder.header(
                        "Cookie",
                        cookies.entrySet().stream()
                                .map(entry -> entry.getKey() + "=" + entry.getValue())
                                .collect(Collectors.joining("; ")));
            }
            if (csrf != null) builder.header("X-XSRF-TOKEN", csrf);
            builder.method(
                    method,
                    body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            try (var client = HttpClient.newHttpClient()) {
                var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                for (var header : response.headers().allValues("Set-Cookie")) {
                    var pair = header.split(";", 2)[0];
                    var index = pair.indexOf('=');
                    var name = pair.substring(0, index);
                    var value = pair.substring(index + 1);
                    if (header.contains("Max-Age=0") || value.isEmpty()) cookies.remove(name);
                    else cookies.put(name, value);
                }
                return response;
            }
        }
    }

    private record Line(UUID sku, int quantity) {}
}
