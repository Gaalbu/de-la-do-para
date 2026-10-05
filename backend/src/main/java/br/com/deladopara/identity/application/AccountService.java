package br.com.deladopara.identity.application;

import br.com.deladopara.identity.adapter.persistence.AccountRepository;
import br.com.deladopara.identity.adapter.persistence.IdentityMailOutboxRepository;
import br.com.deladopara.identity.adapter.persistence.VerificationTokenRepository;
import br.com.deladopara.identity.domain.Account;
import br.com.deladopara.identity.domain.IdentityMailOutbox;
import br.com.deladopara.identity.domain.VerificationToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final VerificationTokenRepository tokens;
    private final IdentityMailOutboxRepository mailOutbox;
    private final SecretCipher cipher;
    private final SecureRandom random;
    private final Duration verificationTtl;
    private final Duration recoveryTtl;
    private final JdbcTemplate jdbc;

    public AccountService(
            AccountRepository accounts,
            PasswordEncoder passwordEncoder,
            Clock clock,
            VerificationTokenRepository tokens,
            IdentityMailOutboxRepository mailOutbox,
            SecretCipher cipher,
            SecureRandom random,
            @org.springframework.beans.factory.annotation.Value("${app.identity.verification-ttl:PT30M}")
                    Duration verificationTtl,
            @org.springframework.beans.factory.annotation.Value("${app.identity.recovery-ttl:PT15M}")
                    Duration recoveryTtl,
            JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.tokens = tokens;
        this.mailOutbox = mailOutbox;
        this.cipher = cipher;
        this.random = random;
        this.verificationTtl = verificationTtl;
        this.recoveryTtl = recoveryTtl;
        this.jdbc = jdbc;
    }

    @Transactional
    public Account register(String rawEmail, String rawPassword, Account.Role role) {
        var email = normalize(rawEmail);
        validateEmail(email);
        validatePassword(rawPassword);
        if (verificationTtl.isNegative() || verificationTtl.isZero()) {
            throw new IllegalStateException("app.identity.verification-ttl must be positive");
        }
        if (accounts.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException();
        }
        var now = clock.instant();
        var account = new Account(UUID.randomUUID(), email, passwordEncoder.encode(rawPassword), role, now);
        Account saved;
        try {
            saved = accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException();
        }
        var token = randomToken();
        var verification = new VerificationToken(
                UUID.randomUUID(),
                saved.getId(),
                sha256(token),
                VerificationToken.TokenType.VERIFY,
                now.plus(verificationTtl),
                now);
        tokens.save(verification);
        mailOutbox.save(new IdentityMailOutbox(
                UUID.randomUUID(),
                saved.getId(),
                verification.getId(),
                VerificationToken.TokenType.VERIFY,
                cipher.encrypt("identity-verification", token),
                now));
        return saved;
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidInputException("IDENTITY_001", "token inválido");
        }
        var token = tokens.findLockedByTokenHash(sha256(rawToken)).orElseThrow(InvalidVerificationTokenException::new);
        var now = clock.instant();
        if (token.getType() != VerificationToken.TokenType.VERIFY
                || token.isUsed()
                || !now.isBefore(token.getExpiresAt())) {
            throw new InvalidVerificationTokenException();
        }
        var account = accounts.findById(token.getAccountId()).orElseThrow(InvalidVerificationTokenException::new);
        account.setEmailVerified(true, now);
        token.markUsed(now);
    }

    @Transactional
    public void requestRecovery(String rawEmail) {
        var email = normalize(rawEmail);
        validateEmail(email);
        var account = accounts.findLockedByEmail(email)
                .filter(Account::isEmailVerified)
                .orElse(null);
        if (account == null) {
            return;
        }

        var now = clock.instant();
        if (recoveryTtl.isNegative() || recoveryTtl.isZero()) {
            throw new IllegalStateException("app.identity.recovery-ttl must be positive");
        }
        var previous =
                tokens.findAllByAccountIdAndTypeAndUsedAtIsNull(account.getId(), VerificationToken.TokenType.RECOVERY);
        if (!previous.isEmpty()) {
            var previousIds = previous.stream().map(VerificationToken::getId).toList();
            mailOutbox.deletePendingForTokens(previousIds);
            tokens.deleteAll(previous);
        }
        var token = randomToken();
        var recovery = new VerificationToken(
                UUID.randomUUID(),
                account.getId(),
                sha256(token),
                VerificationToken.TokenType.RECOVERY,
                now.plus(recoveryTtl),
                now);
        tokens.save(recovery);
        mailOutbox.save(new IdentityMailOutbox(
                UUID.randomUUID(),
                account.getId(),
                recovery.getId(),
                VerificationToken.TokenType.RECOVERY,
                cipher.encrypt("identity-recovery", token),
                now));
    }

    @Transactional
    public void resetPassword(String rawToken, String rawPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidInputException("IDENTITY_001", "token inválido");
        }
        validatePassword(rawPassword);
        var token = tokens.findLockedByTokenHash(sha256(rawToken)).orElseThrow(InvalidVerificationTokenException::new);
        var now = clock.instant();
        if (token.getType() != VerificationToken.TokenType.RECOVERY
                || token.isUsed()
                || !now.isBefore(token.getExpiresAt())) {
            throw new InvalidVerificationTokenException();
        }
        var account = accounts.findById(token.getAccountId()).orElseThrow(InvalidVerificationTokenException::new);
        account.setPasswordHash(passwordEncoder.encode(rawPassword), now);
        token.markUsed(now);
        tokens.consumeOthers(account.getId(), VerificationToken.TokenType.RECOVERY, token.getId(), now);
        jdbc.update("delete from SPRING_SESSION where PRINCIPAL_NAME = ?", account.getEmail());
    }

    public Optional<Buyer> buyer(String email) {
        return accounts.findByEmailIgnoreCase(normalize(email))
                .map(account -> new Buyer(account.getId(), account.getEmail(), account.isEmailVerified()));
    }

    public record Buyer(UUID accountId, String email, boolean emailVerified) {}

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<UUID> accountIdByEmail(String email) {
        return accounts.findByEmailIgnoreCase(normalize(email)).map(Account::getId);
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank() || email.length() > 254 || !email.contains("@")) {
            throw new InvalidInputException("IDENTITY_001", "e-mail inválido");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new InvalidInputException("IDENTITY_001", "senha deve ter 8..72 caracteres");
        }
    }

    public static class DuplicateEmailException extends RuntimeException {}

    public static class InvalidVerificationTokenException extends RuntimeException {}

    public static class InvalidInputException extends RuntimeException {
        private final String codigo;

        public InvalidInputException(String codigo, String message) {
            super(message);
            this.codigo = codigo;
        }

        public String getCodigo() {
            return codigo;
        }
    }
}
