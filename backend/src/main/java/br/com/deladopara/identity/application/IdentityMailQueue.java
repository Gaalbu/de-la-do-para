package br.com.deladopara.identity.application;

import br.com.deladopara.identity.adapter.persistence.AccountRepository;
import br.com.deladopara.identity.adapter.persistence.IdentityMailOutboxRepository;
import br.com.deladopara.identity.adapter.persistence.VerificationTokenRepository;
import br.com.deladopara.identity.domain.IdentityMailOutbox;
import br.com.deladopara.identity.domain.VerificationToken;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityMailQueue {

    private final IdentityMailOutboxRepository outbox;
    private final AccountRepository accounts;
    private final SecretCipher cipher;
    private final Clock clock;
    private final VerificationTokenRepository tokens;
    private final Duration verificationTtl;
    private final Duration recoveryTtl;

    public IdentityMailQueue(
            IdentityMailOutboxRepository outbox,
            AccountRepository accounts,
            VerificationTokenRepository tokens,
            SecretCipher cipher,
            Clock clock,
            @Value("${app.identity.verification-ttl:PT30M}") Duration verificationTtl,
            @Value("${app.identity.recovery-ttl:PT15M}") Duration recoveryTtl) {
        this.outbox = outbox;
        this.accounts = accounts;
        this.tokens = tokens;
        this.cipher = cipher;
        this.clock = clock;
        this.verificationTtl = verificationTtl;
        this.recoveryTtl = recoveryTtl;
    }

    @Transactional
    public UUID claimOne(Duration lease) {
        var due = outbox.lockDue(PageRequest.of(0, 1));
        if (due.isEmpty()) {
            return null;
        }
        var item = due.get(0);
        var now = clock.instant();
        var token = tokens.findLockedById(item.getTokenId()).orElse(null);
        if (token == null || token.isUsed()) {
            item.cancel();
            return null;
        }
        if (!item.claim(now, now.plus(lease))) {
            return null;
        }
        var ttl = token.getType() == VerificationToken.TokenType.VERIFY ? verificationTtl : recoveryTtl;
        token.extendExpiration(now.plus(ttl));
        return item.getId();
    }

    @Transactional(readOnly = true)
    public MailMessage loadMessage(UUID id) {
        var item = outbox.findById(id).orElse(null);
        if (item == null
                || item.getStatus() != IdentityMailOutbox.Status.PENDING
                || item.getEncryptedPayload() == null) {
            return null;
        }
        var account = accounts.findById(item.getAccountId()).orElseThrow();
        var purpose = item.getMessageType() == VerificationToken.TokenType.VERIFY
                ? "identity-verification"
                : "identity-recovery";
        var token = cipher.decrypt(purpose, item.getEncryptedPayload());
        return new MailMessage(account.getEmail(), item.getMessageType(), token);
    }

    @Transactional
    public void accepted(UUID id) {
        var item = outbox.findById(id).orElseThrow();
        item.sent(clock.instant());
    }

    @Transactional
    public void retry(UUID id, Duration delay, String safeErrorCode) {
        var item = outbox.findById(id).orElseThrow();
        item.failed(clock.instant(), clock.instant().plus(delay), safeErrorCode);
    }

    public record MailMessage(String recipient, VerificationToken.TokenType type, String token) {}
}
