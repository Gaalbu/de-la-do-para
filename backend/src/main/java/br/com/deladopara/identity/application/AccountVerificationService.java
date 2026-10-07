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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final AccountService accountService;
    private final VerificationTokenRepository tokens;
    private final IdentityMailPort mail;
    private final IdentityProperties properties;
    private final Clock clock;

    public AccountVerificationService(
            AccountRepository accounts,
            AccountService accountService,
            VerificationTokenRepository tokens,
            IdentityMailPort mail,
            IdentityProperties properties,
            Clock clock) {
        this.accounts = accounts;
        this.accountService = accountService;
        this.tokens = tokens;
        this.mail = mail;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Account register(String email, String password) {
        var account = accountService.register(email, password, Account.Role.CUSTOMER);
        var rawToken = newToken();
        var now = clock.instant();
        tokens.save(new VerificationToken(
                UUID.randomUUID(),
                account.getId(),
                hash(rawToken),
                VerificationToken.TokenType.VERIFY,
                now.plus(properties.verificationTokenTtl()),
                now));
        mail.sendVerification(account.getEmail(), verificationLink(rawToken));
        return account;
    }

    @Transactional
    public Account verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 256) {
            throw new InvalidVerificationTokenException();
        }
        var token = tokens.findForUpdateByTokenHash(hash(rawToken)).orElseThrow(InvalidVerificationTokenException::new);
        var now = clock.instant();
        if (token.getType() != VerificationToken.TokenType.VERIFY || token.isUsed() || token.isExpired(now)) {
            throw new InvalidVerificationTokenException();
        }
        var account = accounts.findById(token.getAccountId()).orElseThrow(InvalidVerificationTokenException::new);
        token.markUsed(now);
        account.setEmailVerified(true, now);
        return account;
    }

    static String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String newToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String verificationLink(String token) {
        return properties.verificationUrl() + "#token=" + token;
    }

    public static class InvalidVerificationTokenException extends RuntimeException {}
}
