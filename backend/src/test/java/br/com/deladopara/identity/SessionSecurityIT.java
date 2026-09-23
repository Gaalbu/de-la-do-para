package br.com.deladopara.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.identity.application.AccountService;
import br.com.deladopara.identity.domain.Account;
import br.com.deladopara.support.PostgresTestContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestContainer.class)
class SessionSecurityIT {
    private static final String PASSWORD = "correct-horse-battery";

    private final int port;
    private final AccountService accounts;
    private final JdbcTemplate jdbc;

    @Autowired
    SessionSecurityIT(@Value("${local.server.port}") int port, AccountService accounts, JdbcTemplate jdbc) {
        this.port = port;
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    @Test
    void registrationRequiresCsrfAndCreatesUnverifiedCustomer() throws Exception {
        var email = uniqueEmail();
        var body = credentials(email, PASSWORD);

        assertThat(send("POST", "/api/v1/accounts", body, new Client()).statusCode())
                .isEqualTo(403);

        var client = new Client();
        client.fetchCsrf();
        var created = client.post("/api/v1/accounts", body);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("Location")).isPresent();
        assertThat(created.body())
                .contains("\"emailVerified\":false", "\"role\":\"CUSTOMER\"")
                .doesNotContain(PASSWORD, "password");
    }

    @Test
    void duplicateEmailIgnoringCaseIsRejectedWithoutSecondAccount() throws Exception {
        var email = uniqueEmail();
        var client = new Client();
        client.fetchCsrf();
        assertThat(client.post("/api/v1/accounts", credentials(email, PASSWORD)).statusCode())
                .isEqualTo(201);

        var duplicate = client.post("/api/v1/accounts", credentials(email.toUpperCase(), PASSWORD));

        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("IDENTITY_002");
    }

    @Test
    void invalidRegistrationInputIsRejected() throws Exception {
        var client = new Client();
        client.fetchCsrf();
        assertThat(client.post("/api/v1/accounts", credentials("not-an-email", PASSWORD))
                        .statusCode())
                .isEqualTo(400);
        assertThat(client.post("/api/v1/accounts", credentials(uniqueEmail(), "short"))
                        .statusCode())
                .isEqualTo(400);
    }

    @Test
    void wrongPasswordAndUnknownAccountLookIdenticalAndSetNoCookie() throws Exception {
        var email = registerCustomer();
        var client = new Client();
        client.fetchCsrf();

        var wrong = client.post("/api/v1/sessions", credentials(email, "not-the-password"));
        var unknown = client.post("/api/v1/sessions", credentials(uniqueEmail(), PASSWORD));

        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertThat(wrong.body()).contains("IDENTITY_005");
        assertThat(unknown.body()).contains("IDENTITY_005");
        assertThat(wrong.headers().allValues("Set-Cookie")).noneMatch(c -> c.startsWith("DLSESSION"));
    }

    @Test
    void protectedRoutesRejectMissingSession() throws Exception {
        var response = send("GET", "/api/v1/sessions/current", null, new Client());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("IDENTITY_006");
    }

    @Test
    void loginIssuesProtectedSessionCookieAndCurrentReturnsAccount() throws Exception {
        var email = registerCustomer();
        var client = loggedIn(email);

        var current = client.get("/api/v1/sessions/current");

        assertThat(current.statusCode()).isEqualTo(200);
        assertThat(current.body()).contains(email).doesNotContain("passwordHash", PASSWORD);
        var setCookie = client.lastLoginSetCookie;
        assertThat(setCookie).startsWith("DLSESSION=").contains("HttpOnly", "Secure", "SameSite=Lax");
    }

    @Test
    void sessionsArePersistedInPostgresAndRemovedOnLogout() throws Exception {
        var email = registerCustomer();
        var client = loggedIn(email);
        var sql = "select count(*) from spring_session where principal_name = ?";

        assertThat(jdbc.queryForObject(sql, Integer.class, email)).isEqualTo(1);

        client.delete("/api/v1/sessions/current");
        assertThat(jdbc.queryForObject(sql, Integer.class, email)).isZero();
    }

    @Test
    void loginRotatesAnExistingSessionIdentifier() throws Exception {
        var email = registerCustomer();
        var client = new Client();
        client.fetchCsrf();
        var before = client.cookies.get("DLSESSION");

        client.post("/api/v1/sessions", credentials(email, PASSWORD));

        assertThat(client.cookies.get("DLSESSION")).isNotNull().isNotEqualTo(before);
    }

    @Test
    void mutationsWithSessionRequireCsrf() throws Exception {
        var client = loggedIn(registerCustomer());
        client.sendCsrf = false;
        var withoutHeader = client.delete("/api/v1/sessions/current");
        client.sendCsrf = true;

        assertThat(withoutHeader.statusCode()).isEqualTo(403);
        assertThat(client.get("/api/v1/sessions/current").statusCode()).isEqualTo(200);
    }

    @Test
    void logoutInvalidatesTheSessionServerSide() throws Exception {
        var client = loggedIn(registerCustomer());
        var stolenCookie = client.cookies.get("DLSESSION");

        assertThat(client.delete("/api/v1/sessions/current").statusCode()).isEqualTo(204);

        var replay = new Client();
        replay.cookies.put("DLSESSION", stolenCookie);
        assertThat(replay.get("/api/v1/sessions/current").statusCode()).isEqualTo(401);
    }

    @Test
    void customerCannotReachAdminRoutesButAdminPassesTheGuard() throws Exception {
        var customer = loggedIn(registerCustomer());
        var denied = customer.get("/api/v1/admin/anything");
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(denied.body()).contains("IDENTITY_008");

        var adminEmail = uniqueEmail();
        accounts.register(adminEmail, PASSWORD, Account.Role.ADMIN);
        var admin = loggedIn(adminEmail);
        assertThat(admin.get("/api/v1/admin/anything").statusCode()).isEqualTo(404);
    }

    private String registerCustomer() {
        var email = uniqueEmail();
        accounts.register(email, PASSWORD, Account.Role.CUSTOMER);
        return email;
    }

    private Client loggedIn(String email) throws Exception {
        var client = new Client();
        client.fetchCsrf();
        var response = client.post("/api/v1/sessions", credentials(email, PASSWORD));
        assertThat(response.statusCode()).isEqualTo(200);
        client.lastLoginSetCookie = response.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("DLSESSION"))
                .findFirst()
                .orElseThrow();
        client.fetchCsrf();
        return client;
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private HttpResponse<String> send(String method, String path, String body, Client client) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (!client.cookies.isEmpty()) {
            builder.header(
                    "Cookie",
                    client.cookies.entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining("; ")));
        }
        if (client.csrf != null && client.sendCsrf) {
            builder.header("X-XSRF-TOKEN", client.csrf);
        }
        builder.method(
                method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var http = HttpClient.newHttpClient()) {
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            client.absorb(response);
            return response;
        }
    }

    private final class Client {
        final Map<String, String> cookies = new LinkedHashMap<>();
        String csrf;
        boolean sendCsrf = true;
        String lastLoginSetCookie;

        void fetchCsrf() throws Exception {
            var response = send("GET", "/api/v1/csrf", null, this);
            assertThat(response.statusCode()).isEqualTo(200);
            csrf = cookies.get("XSRF-TOKEN");
        }

        HttpResponse<String> get(String path) throws Exception {
            return send("GET", path, null, this);
        }

        HttpResponse<String> post(String path, String body) throws Exception {
            return send("POST", path, body, this);
        }

        HttpResponse<String> delete(String path) throws Exception {
            return send("DELETE", path, null, this);
        }

        void absorb(HttpResponse<String> response) {
            for (var header : response.headers().allValues("Set-Cookie")) {
                var pair = header.split(";", 2)[0];
                var idx = pair.indexOf('=');
                var name = pair.substring(0, idx);
                var value = pair.substring(idx + 1);
                if (header.contains("Max-Age=0") || value.isEmpty()) {
                    cookies.remove(name);
                } else {
                    cookies.put(name, value);
                }
            }
        }
    }
}
