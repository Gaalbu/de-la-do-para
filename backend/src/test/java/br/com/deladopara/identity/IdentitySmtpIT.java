package br.com.deladopara.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.identity.adapter.mail.SmtpIdentityMailAdapter;
import br.com.deladopara.identity.config.IdentityProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class IdentitySmtpIT {

    @Container
    private static final GenericContainer<?> MAILPIT = new GenericContainer<>(
                    DockerImageName.parse("axllent/mailpit:v1.31.2"))
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/info").forPort(8025));

    @Test
    void smtpAdapterDeliversTheVerificationLinkToMailpit() throws Exception {
        var sender = new JavaMailSenderImpl();
        sender.setHost(MAILPIT.getHost());
        sender.setPort(MAILPIT.getMappedPort(1025));
        var properties = new IdentityProperties(
                10, Duration.ofMinutes(30), "https://loja.example/verify-email", "no-reply@deladopara.local");
        var adapter = new SmtpIdentityMailAdapter(sender, properties);
        var email = "smtp-" + java.util.UUID.randomUUID() + "@example.test";

        adapter.sendVerification(email, "https://loja.example/verify-email#token=opaque-token");

        var request = HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost() + ":"
                        + MAILPIT.getMappedPort(8025) + "/api/v1/message/latest/raw"))
                .GET()
                .build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(email);
        var message = new MimeMessage(
                Session.getInstance(new Properties()),
                new ByteArrayInputStream(response.body().getBytes(StandardCharsets.UTF_8)));
        assertThat(message.getContent().toString()).contains("https://loja.example/verify-email#token=opaque-token");
    }
}
