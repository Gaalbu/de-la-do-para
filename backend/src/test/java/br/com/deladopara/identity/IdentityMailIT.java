package br.com.deladopara.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.identity.adapter.persistence.AccountRepository;
import br.com.deladopara.identity.adapter.persistence.IdentityMailOutboxRepository;
import br.com.deladopara.identity.application.AccountService;
import br.com.deladopara.identity.application.IdentityMailQueue;
import br.com.deladopara.identity.application.SecretCipher;
import br.com.deladopara.identity.domain.Account;
import br.com.deladopara.identity.infrastructure.IdentityMailWorker;
import br.com.deladopara.support.PostgresTestContainer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Import(PostgresTestContainer.class)
@Testcontainers
class IdentityMailIT {

    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.2"))
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025).forStatusCode(200));

    @DynamicPropertySource
    static void smtpProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    private final AccountService accounts;
    private final AccountRepository accountRepository;
    private final IdentityMailOutboxRepository outbox;
    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final IdentityMailQueue mailQueue;
    private final JavaMailSender smtpMailSender;

    @Autowired
    IdentityMailIT(
            AccountService accounts,
            AccountRepository accountRepository,
            IdentityMailOutboxRepository outbox,
            JdbcTemplate jdbc,
            SecretCipher cipher,
            PasswordEncoder encoder,
            Clock clock,
            IdentityMailQueue mailQueue,
            JavaMailSender smtpMailSender) {
        this.accounts = accounts;
        this.accountRepository = accountRepository;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.encoder = encoder;
        this.clock = clock;
        this.mailQueue = mailQueue;
        this.smtpMailSender = smtpMailSender;
    }

    @Test
    void registrationStoresOnlyEncryptedTokenAndVerificationIsSingleUse() {
        var account = accounts.register(
                "verify-" + UUID.randomUUID() + "@example.com", "correct-horse", Account.Role.CUSTOMER);
        var itemId = jdbc.queryForObject(
                "select id from identity_mail_outbox where account_id = ?", UUID.class, account.getId());
        var rawCiphertext = jdbc.queryForObject(
                "select encrypted_payload from identity_mail_outbox where id = ?", String.class, itemId);
        assertThat(rawCiphertext).isNotBlank();
        var token = cipher.decrypt("identity-verification", rawCiphertext);
        assertThat(jdbc.queryForObject(
                        "select token_hash from verification_tokens where account_id = ?",
                        String.class,
                        account.getId()))
                .isNotEqualTo(token)
                .hasSize(64);

        accounts.verifyEmail(token);
        assertThat(accountRepository.findById(account.getId()).orElseThrow().isEmailVerified())
                .isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accounts.verifyEmail(token))
                .isInstanceOf(AccountService.InvalidVerificationTokenException.class);
    }

    @Test
    void verificationWorkerDeliversToMailpitAndClearsCiphertextAfterAcceptance() throws Exception {
        var email = "mailpit-" + UUID.randomUUID() + "@example.com";
        var account = accounts.register(email, "correct-horse", Account.Role.CUSTOMER);
        var itemId = jdbc.queryForObject(
                "select id from identity_mail_outbox where account_id = ?", UUID.class, account.getId());
        var rawToken = cipher.decrypt(
                "identity-verification",
                jdbc.queryForObject(
                        "select encrypted_payload from identity_mail_outbox where id = ?", String.class, itemId));
        jdbc.update(
                "update identity_mail_outbox set available_at = ?, lease_until = null "
                        + "where status = 'PENDING' and id <> ?",
                java.sql.Timestamp.from(clock.instant().plus(Duration.ofHours(1))),
                itemId);

        var worker = new IdentityMailWorker(
                mailQueue,
                smtpMailSender,
                Duration.ofMinutes(2),
                Duration.ofSeconds(30),
                "no-reply@deladopara.local",
                "http://localhost:4200",
                1);
        worker.poll();

        var sent = outbox.findById(itemId).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(br.com.deladopara.identity.domain.IdentityMailOutbox.Status.SENT);
        assertThat(sent.getAttemptCount()).isEqualTo(1);
        assertThat(sent.getEncryptedPayload()).isNull();
        var rawMessage = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost() + ":"
                                        + MAILPIT.getMappedPort(8025) + "/api/v1/message/latest/raw"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(rawMessage.statusCode()).isEqualTo(200);
        var received = new MimeMessage(
                Session.getInstance(new Properties()),
                new ByteArrayInputStream(rawMessage.body().getBytes(StandardCharsets.UTF_8)));
        assertThat(received.getAllRecipients())
                .anySatisfy(recipient -> assertThat(recipient.toString()).contains(email));
        assertThat(received.getSubject()).contains("Confirme seu e-mail");
        assertThat(received.getContent().toString())
                .contains("/verify-email#token=" + rawToken)
                .doesNotContain("?token=", "password", "X-Order-Token");
    }

    @Test
    void expiredTokenCannotBeConsumed() throws Exception {
        var account = accounts.register(
                "expired-" + UUID.randomUUID() + "@example.com", "correct-horse", Account.Role.CUSTOMER);
        var token = jdbc.queryForObject(
                "select encrypted_payload from identity_mail_outbox where account_id = ?",
                String.class,
                account.getId());
        var raw = cipher.decrypt("identity-verification", token);
        var hash = java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update(
                "update verification_tokens set expires_at = now() - interval '1 second' where token_hash = ?", hash);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accounts.verifyEmail(raw))
                .isInstanceOf(AccountService.InvalidVerificationTokenException.class);
    }

    @Test
    void concurrentVerificationConsumesTokenOnlyOnce() throws Exception {
        var account = accounts.register(
                "race-verify-" + UUID.randomUUID() + "@example.com", "correct-horse", Account.Role.CUSTOMER);
        var encrypted = jdbc.queryForObject(
                "select encrypted_payload from identity_mail_outbox where account_id = ?",
                String.class,
                account.getId());
        var raw = cipher.decrypt("identity-verification", encrypted);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> verifyAfter(start, raw));
            var second = pool.submit(() -> verifyAfter(start, raw));
            start.countDown();
            assertThat(first.get() ^ second.get()).isTrue();
        }
    }

    private boolean verifyAfter(CountDownLatch start, String token) throws InterruptedException {
        start.await();
        try {
            accounts.verifyEmail(token);
            return true;
        } catch (AccountService.InvalidVerificationTokenException e) {
            return false;
        }
    }

    @Test
    void acceptedMailClearsCiphertextAndFailureRetainsIt() {
        var account = accounts.register(
                "queue-" + UUID.randomUUID() + "@example.com", "correct-horse", Account.Role.CUSTOMER);
        var itemId = jdbc.queryForObject(
                "select id from identity_mail_outbox where account_id = ?", UUID.class, account.getId());
        var entity = outbox.findById(itemId).orElseThrow();
        var now = clock.instant();
        entity.claim(now, now.plus(Duration.ofMinutes(1)));
        outbox.saveAndFlush(entity);
        entity.failed(now, now.plusSeconds(30), "MailSendException");
        outbox.saveAndFlush(entity);
        assertThat(outbox.findById(itemId).orElseThrow().getEncryptedPayload()).isNotBlank();
        entity.sent(now.plusSeconds(31));
        outbox.saveAndFlush(entity);
        assertThat(outbox.findById(itemId).orElseThrow().getEncryptedPayload()).isNull();
    }

    @Test
    void recoveryMailIsEncryptedAndPasswordResetConsumesTokenAndChangesPassword() {
        var email = "recovery-" + UUID.randomUUID() + "@example.com";
        var account = accounts.register(email, "correct-horse", Account.Role.CUSTOMER);
        var verifyCiphertext = jdbc.queryForObject(
                "select encrypted_payload from identity_mail_outbox where account_id = ? and message_type = 'VERIFY'",
                String.class,
                account.getId());
        accounts.verifyEmail(cipher.decrypt("identity-verification", verifyCiphertext));

        accounts.requestRecovery(email);
        var recoveryCiphertext = jdbc.queryForObject(
                "select encrypted_payload from identity_mail_outbox where account_id = ? and message_type = 'RECOVERY'",
                String.class,
                account.getId());
        var recoveryToken = cipher.decrypt("identity-recovery", recoveryCiphertext);
        accounts.resetPassword(recoveryToken, "new-correct-horse");

        assertThat(encoder.matches(
                        "new-correct-horse",
                        accountRepository
                                .findById(account.getId())
                                .orElseThrow()
                                .getPasswordHash()))
                .isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> accounts.resetPassword(recoveryToken, "other-password"))
                .isInstanceOf(AccountService.InvalidVerificationTokenException.class);
    }

    @Test
    void expiredAndInvalidRecoveryTokensCannotChangePassword() throws Exception {
        var email = "expired-recovery-" + UUID.randomUUID() + "@example.com";
        var account = accounts.register(email, "correct-horse", Account.Role.CUSTOMER);
        var verifyCiphertext = jdbc.queryForObject(
                "select encrypted_payload from identity_mail_outbox where account_id = ? and message_type = 'VERIFY'",
                String.class,
                account.getId());
        accounts.verifyEmail(cipher.decrypt("identity-verification", verifyCiphertext));
        accounts.requestRecovery(email);
        var encrypted = jdbc.queryForObject(
                "select encrypted_payload from identity_mail_outbox where account_id = ? and message_type = 'RECOVERY'",
                String.class,
                account.getId());
        var token = cipher.decrypt("identity-recovery", encrypted);
        var tokenHash = java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(token.getBytes(StandardCharsets.UTF_8)));
        jdbc.update(
                "update verification_tokens set expires_at = now() - interval '1 second' where token_hash = ?",
                tokenHash);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accounts.resetPassword(token, "new-password"))
                .isInstanceOf(AccountService.InvalidVerificationTokenException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> accounts.resetPassword("not-a-valid-token", "other-password"))
                .isInstanceOf(AccountService.InvalidVerificationTokenException.class);
        assertThat(encoder.matches(
                        "correct-horse",
                        accountRepository
                                .findById(account.getId())
                                .orElseThrow()
                                .getPasswordHash()))
                .isTrue();
    }

    @Test
    void concurrentRecoveryConsumesTokenOnlyOnce() throws Exception {
        var email = "race-recovery-" + UUID.randomUUID() + "@example.com";
        var account = accounts.register(email, "correct-horse", Account.Role.CUSTOMER);
        var verifyCiphertext = jdbc.queryForObject(
                "select encrypted_payload from identity_mail_outbox where account_id = ? and message_type = 'VERIFY'",
                String.class,
                account.getId());
        accounts.verifyEmail(cipher.decrypt("identity-verification", verifyCiphertext));
        accounts.requestRecovery(email);
        var encrypted = jdbc.queryForObject(
                "select encrypted_payload from identity_mail_outbox where account_id = ? and message_type = 'RECOVERY'",
                String.class,
                account.getId());
        var token = cipher.decrypt("identity-recovery", encrypted);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> resetAfter(start, token, "first-new-password"));
            var second = pool.submit(() -> resetAfter(start, token, "second-new-password"));
            start.countDown();
            assertThat(first.get() ^ second.get()).isTrue();
        }
        var passwordHash =
                accountRepository.findById(account.getId()).orElseThrow().getPasswordHash();
        assertThat(encoder.matches("first-new-password", passwordHash)
                        || encoder.matches("second-new-password", passwordHash))
                .isTrue();
    }

    @Test
    void recoveryWorkerDeliversResetLinkToMailpit() throws Exception {
        var email = "recovery-mailpit-" + UUID.randomUUID() + "@example.com";
        var account = accounts.register(email, "correct-horse", Account.Role.CUSTOMER);
        var verifyCiphertext = jdbc.queryForObject(
                "select encrypted_payload from identity_mail_outbox where account_id = ? and message_type = 'VERIFY'",
                String.class,
                account.getId());
        accounts.verifyEmail(cipher.decrypt("identity-verification", verifyCiphertext));
        accounts.requestRecovery(email);
        var itemId = jdbc.queryForObject(
                "select id from identity_mail_outbox where account_id = ? and message_type = 'RECOVERY'",
                UUID.class,
                account.getId());
        var rawToken = cipher.decrypt(
                "identity-recovery",
                jdbc.queryForObject(
                        "select encrypted_payload from identity_mail_outbox where id = ?", String.class, itemId));
        jdbc.update(
                "update identity_mail_outbox set available_at = ?, lease_until = null "
                        + "where status = 'PENDING' and id <> ?",
                java.sql.Timestamp.from(clock.instant().plus(Duration.ofHours(1))),
                itemId);

        var worker = new IdentityMailWorker(
                mailQueue,
                smtpMailSender,
                Duration.ofMinutes(2),
                Duration.ofSeconds(30),
                "no-reply@deladopara.local",
                "http://localhost:4200",
                1);
        worker.poll();

        assertThat(outbox.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(br.com.deladopara.identity.domain.IdentityMailOutbox.Status.SENT);
        var rawMessage = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost() + ":"
                                        + MAILPIT.getMappedPort(8025) + "/api/v1/message/latest/raw"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        var received = new MimeMessage(
                Session.getInstance(new Properties()),
                new ByteArrayInputStream(rawMessage.body().getBytes(StandardCharsets.UTF_8)));
        assertThat(received.getAllRecipients())
                .anySatisfy(recipient -> assertThat(recipient.toString()).contains(email));
        assertThat(received.getSubject()).contains("Redefina sua senha");
        assertThat(received.getContent().toString())
                .contains("/reset-password#token=" + rawToken)
                .doesNotContain("?token=", "password=", "X-Order-Token");
    }

    private boolean resetAfter(CountDownLatch start, String token, String newPassword) throws InterruptedException {
        start.await();
        try {
            accounts.resetPassword(token, newPassword);
            return true;
        } catch (AccountService.InvalidVerificationTokenException e) {
            return false;
        }
    }
}
