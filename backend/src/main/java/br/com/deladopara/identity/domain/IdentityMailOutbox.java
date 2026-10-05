package br.com.deladopara.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_mail_outbox")
public class IdentityMailOutbox {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "token_id", nullable = false)
    private UUID tokenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 16)
    private VerificationToken.TokenType messageType;

    @Column(name = "encrypted_payload", columnDefinition = "text")
    private String encryptedPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdentityMailOutbox() {}

    public IdentityMailOutbox(
            UUID id,
            UUID accountId,
            UUID tokenId,
            VerificationToken.TokenType messageType,
            String encryptedPayload,
            Instant now) {
        this.id = id;
        this.accountId = accountId;
        this.tokenId = tokenId;
        this.messageType = messageType;
        this.encryptedPayload = encryptedPayload;
        this.status = Status.PENDING;
        this.availableAt = now;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getTokenId() {
        return tokenId;
    }

    public VerificationToken.TokenType getMessageType() {
        return messageType;
    }

    public String getEncryptedPayload() {
        return encryptedPayload;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public boolean claim(Instant now, Instant leaseUntil) {
        if (status != Status.PENDING
                || availableAt.isAfter(now)
                || (this.leaseUntil != null && this.leaseUntil.isAfter(now))) {
            return false;
        }
        this.leaseUntil = leaseUntil;
        this.attemptCount++;
        return true;
    }

    public void sent(Instant now) {
        this.status = Status.SENT;
        this.encryptedPayload = null;
        this.sentAt = now;
        this.leaseUntil = null;
        this.lastErrorCode = null;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
        this.encryptedPayload = null;
        this.leaseUntil = null;
        this.lastErrorCode = null;
    }

    public void failed(Instant now, Instant retryAt, String safeErrorCode) {
        this.availableAt = retryAt;
        this.leaseUntil = null;
        this.lastErrorCode = safeErrorCode;
    }

    public enum Status {
        PENDING,
        SENT,
        CANCELLED
    }
}
