package br.com.deladopara.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.support.PostgresTestContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestContainer.class, IdentityMailIT.MailCaptureConfiguration.class})
class IdentityMailIT {

    private static final String PASSWORD = "correct-horse-battery";

    private final int port;
    private final JdbcTemplate jdbc;
    private final CapturingMailSender mailSender;

    @Autowired
    IdentityMailIT(@Value("${local.server.port}") int port, JdbcTemplate jdbc, CapturingMailSender mailSender) {
        this.port = port;
        this.jdbc = jdbc;
        this.mailSender = mailSender;
    }

    @Test
    void registrationSendsHashedSingleUseVerificationTokenAndKeepsCustomerRole() throws Exception {
        var email = "verify-" + UUID.randomUUID() + "@example.com";

        var client = new Client();
        client.fetchCsrf();
        var registered = client.post("/api/v1/accounts", credentials(email, PASSWORD));
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(registered.body()).contains("\"emailVerified\":false", "\"role\":\"CUSTOMER\"");

        var message = mailSender.lastMessage();
        assertThat(message.getTo()).containsExactly(email);
        assertThat(message.getText()).contains("https://localhost/verify-email#token=");
        var token = message.getText().replaceAll("(?s).*#token=([A-Za-z0-9_-]+).*", "$1");
        assertThat(token).matches("[A-Za-z0-9_-]{40,64}");
        var tokenHash = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForObject(
                        "select token_hash from verification_tokens where token_hash = ?", String.class, tokenHash))
                .isEqualTo(tokenHash);
        assertThat(jdbc.queryForObject("select email_verified from accounts where email = ?", Boolean.class, email))
                .isFalse();

        var verified = client.postWithoutCsrf("/api/v1/accounts/verify", "{\"token\":\"" + token + "\"}");
        assertThat(verified.statusCode()).isEqualTo(200);
        assertThat(verified.body()).contains("\"emailVerified\":true");

        assertThat(client.postWithoutCsrf("/api/v1/accounts/verify", "{\"token\":\"" + token + "\"}")
                        .statusCode())
                .isEqualTo(410);
    }

    @Test
    void expiredVerificationTokenCannotVerifyAccount() throws Exception {
        var email = "expired-" + UUID.randomUUID() + "@example.com";
        var client = new Client();
        client.fetchCsrf();
        assertThat(client.post("/api/v1/accounts", credentials(email, PASSWORD)).statusCode())
                .isEqualTo(201);

        var message = mailSender.lastMessage();
        var token = message.getText().replaceAll("(?s).*#token=([A-Za-z0-9_-]+).*", "$1");
        var tokenHash = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        jdbc.update(
                "update verification_tokens set expires_at = now() - interval '1 second' where token_hash = ?",
                tokenHash);

        assertThat(client.postWithoutCsrf("/api/v1/accounts/verify", "{\"token\":\"" + token + "\"}")
                        .statusCode())
                .isEqualTo(410);
        assertThat(jdbc.queryForObject("select email_verified from accounts where email = ?", Boolean.class, email))
                .isFalse();
    }

    @Test
    void smtpFailureReturnsUnavailableAndRollsBackTheUnverifiedAccount() throws Exception {
        var email = "smtp-failure-" + UUID.randomUUID() + "@example.com";
        var client = new Client();
        client.fetchCsrf();
        var tokenCountBefore = jdbc.queryForObject("select count(*) from verification_tokens", Integer.class);
        mailSender.failNextSend();

        var response = client.post("/api/v1/accounts", credentials(email, PASSWORD));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("IDENTITY_009");
        assertThat(jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, email))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from verification_tokens", Integer.class))
                .isEqualTo(tokenCountBefore);
    }

    @Test
    void concurrentRegistrationsForSameEmailCreateOnlyOneAccountAndDoNotExposeTheConflict() throws Exception {
        var email = "concurrent-" + UUID.randomUUID() + "@example.com";
        var firstClient = new Client();
        var secondClient = new Client();
        firstClient.fetchCsrf();
        secondClient.fetchCsrf();
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentRegistration(firstClient, email, ready, start));
            var second = executor.submit(() -> concurrentRegistration(secondClient, email, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var statuses = java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, email))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from verification_tokens where account_id = "
                                + "(select id from accounts where email = ?)",
                        Integer.class,
                        email))
                .isEqualTo(1);
    }

    private int concurrentRegistration(
            Client client,
            String email,
            java.util.concurrent.CountDownLatch ready,
            java.util.concurrent.CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        return client.post("/api/v1/accounts", credentials(email, PASSWORD)).statusCode();
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private final class Client {
        private final Map<String, String> cookies = new LinkedHashMap<>();
        private String csrf;

        void fetchCsrf() throws Exception {
            var response = send("GET", "/api/v1/csrf", null, false);
            assertThat(response.statusCode()).isEqualTo(200);
            csrf = cookies.get("XSRF-TOKEN");
        }

        HttpResponse<String> post(String path, String body) throws Exception {
            return send("POST", path, body, true);
        }

        HttpResponse<String> postWithoutCsrf(String path, String body) throws Exception {
            return send("POST", path, body, false);
        }

        private HttpResponse<String> send(String method, String path, String body, boolean includeCsrf)
                throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json");
            if (!cookies.isEmpty()) {
                builder.header(
                        "Cookie",
                        cookies.entrySet().stream()
                                .map(entry -> entry.getKey() + "=" + entry.getValue())
                                .collect(Collectors.joining("; ")));
            }
            if (includeCsrf && csrf != null) {
                builder.header("X-XSRF-TOKEN", csrf);
            }
            builder.method(
                    method,
                    body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            try (var http = HttpClient.newHttpClient()) {
                var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                response.headers().allValues("Set-Cookie").forEach(value -> {
                    var pair = value.split(";", 2)[0].split("=", 2);
                    if (pair.length == 2) {
                        cookies.put(pair[0], pair[1]);
                    }
                });
                return response;
            }
        }
    }

    @TestConfiguration
    static class MailCaptureConfiguration {
        @Bean
        CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }

    static class CapturingMailSender extends JavaMailSenderImpl {
        private volatile SimpleMailMessage lastMessage;
        private volatile boolean failNextSend;

        @Override
        public void send(SimpleMailMessage message) {
            if (failNextSend) {
                failNextSend = false;
                throw new MailSendException("SMTP unavailable");
            }
            lastMessage = new SimpleMailMessage(message);
        }

        void failNextSend() {
            failNextSend = true;
        }

        SimpleMailMessage lastMessage() {
            return lastMessage;
        }
    }
}
