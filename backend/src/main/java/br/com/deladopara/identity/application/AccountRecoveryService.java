package br.com.deladopara.identity.application;

import br.com.deladopara.identity.adapter.persistence.AccountRepository;
import br.com.deladopara.identity.adapter.persistence.VerificationTokenRepository;
import br.com.deladopara.identity.config.IdentityProperties;
import br.com.deladopara.identity.domain.Account;
import br.com.deladopara.identity.domain.VerificationToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountRecoveryService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AccountRepository accounts;
    private final VerificationTokenRepository tokens;
    private final IdentityMailPort mail;
    private final IdentityProperties properties;
    private final RecoveryRateLimiter limiter;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AccountRecoveryService(
            AccountRepository accounts,
            VerificationTokenRepository tokens,
            IdentityMailPort mail,
            IdentityProperties properties,
            RecoveryRateLimiter limiter,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbc,
            Clock clock) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.mail = mail;
        this.properties = properties;
        this.limiter = limiter;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void request(String email, String ip) {
        var normalized = AccountService.normalize(email);
        var retryAfter = limiter.retryAfterSeconds(ip, normalized);
        if (retryAfter > 0) {
            throw new RecoveryRateLimitException(retryAfter);
        }
        if (normalized == null) {
            return;
        }
        var account = accounts.findByEmailIgnoreCase(normalized)
                .filter(Account::isEmailVerified)
                .filter(value -> value.getRole() == Account.Role.CUSTOMER);
        if (account.isEmpty()) {
            return;
        }

        var current = accounts.findForUpdateById(account.get().getId()).orElse(null);
        if (current == null || !current.isEmailVerified() || current.getRole() != Account.Role.CUSTOMER) {
            return;
        }
        var rawToken = newToken();
        var now = clock.instant();
        tokens.markAllUnusedByAccountAndType(current.getId(), VerificationToken.TokenType.RECOVERY, now);
        tokens.save(new VerificationToken(
                UUID.randomUUID(),
                current.getId(),
                hash(rawToken),
                VerificationToken.TokenType.RECOVERY,
                now.plus(properties.recoveryTokenTtl()),
                now));
        try {
            mail.sendRecovery(
                    current.getEmail(), properties.recoveryUrl() + "#token=" + rawToken, properties.recoveryTokenTtl());
        } catch (MailException e) {
            tokens.findByTokenHash(hash(rawToken)).ifPresent(token -> token.markUsed(now));
            LOGGER.warn("Password recovery mail could not be sent");
        }
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) {
            throw new InvalidRecoveryTokenException();
        }
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72) {
            throw new AccountService.InvalidInputException("IDENTITY_001", "senha deve ter 8..72 caracteres");
        }
        var tokenHash = hash(rawToken);
        var accountId = tokens.findAccountIdByTokenHash(tokenHash).orElseThrow(InvalidRecoveryTokenException::new);
        var account = accounts.findForUpdateById(accountId).orElseThrow(InvalidRecoveryTokenException::new);
        var now = clock.instant();
        if (!account.isEmailVerified() || account.getRole() != Account.Role.CUSTOMER) {
            throw new InvalidRecoveryTokenException();
        }
        if (tokens.consumeIfActive(tokenHash, account.getId(), VerificationToken.TokenType.RECOVERY, now) != 1) {
            throw new InvalidRecoveryTokenException();
        }
        account.resetPasswordHash(passwordEncoder.encode(newPassword), now);
        tokens.markAllUnusedByAccountAndType(account.getId(), VerificationToken.TokenType.RECOVERY, now);
        jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", account.getEmail());
    }

    private static String newToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static class InvalidRecoveryTokenException extends RuntimeException {}
}
