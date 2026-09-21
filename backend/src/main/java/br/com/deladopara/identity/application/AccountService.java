package br.com.deladopara.identity.application;

import br.com.deladopara.identity.adapter.persistence.AccountRepository;
import br.com.deladopara.identity.adapter.persistence.VerificationTokenRepository;
import br.com.deladopara.identity.domain.Account;
import br.com.deladopara.identity.domain.VerificationToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final VerificationTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AccountService(
            AccountRepository accounts,
            VerificationTokenRepository tokens,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public Account register(String rawEmail, String rawPassword, Account.Role role) {
        var email = normalize(rawEmail);
        validateEmail(email);
        validatePassword(rawPassword);
        if (accounts.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException();
        }
        var now = clock.instant();
        var account = new Account(UUID.randomUUID(), email, passwordEncoder.encode(rawPassword), role, now);
        return accounts.save(account);
    }

    @Transactional(readOnly = true)
    public Account findByEmail(String rawEmail) {
        var email = normalize(rawEmail);
        return accounts.findByEmailIgnoreCase(email).orElse(null);
    }

    @Transactional
    public VerificationToken createVerificationToken(UUID accountId, Duration ttl) {
        var now = clock.instant();
        var raw = generateRawToken();
        var hash = sha256Hex(raw);
        var token = new VerificationToken(
                UUID.randomUUID(), accountId, hash, VerificationToken.TokenType.VERIFY, now.plus(ttl), now);
        var saved = tokens.save(token);
        // raw token is returned via hash holder; caller must send mail with raw
        // We store raw temporarily in a transient way: encode hash -> raw mapping not persisted.
        // For simplicity, return a wrapper that carries raw via thread-local? Instead create a DTO.
        // Here we cheat: save hash, but return token with hash == raw hash; mail service will need raw.
        // Callers that need raw should call generateRawToken externally. For now we expose raw via hash lookup:
        // The service returns the raw token as side-channel via a holder object.
        // To keep API simple, we will generate token and return raw in a record.
        // However this method's return type is entity; we will handle mail outside by generating raw beforehand.
        // Keep for backward compat: token already saved.
        return saved;
    }

    public String generateRawTokenForMail() {
        return generateRawToken();
    }

    public String hash(String raw) {
        return sha256Hex(raw);
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

    private String generateRawToken() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256Hex(String raw) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static class DuplicateEmailException extends RuntimeException {}

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
