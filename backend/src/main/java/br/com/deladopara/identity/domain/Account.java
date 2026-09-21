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
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected Account() {}

    public Account(UUID id, String email, String passwordHash, Role role, Instant now) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.emailVerified = false;
        this.role = role;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setPasswordHash(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    public void setEmailVerified(boolean verified, Instant now) {
        this.emailVerified = verified;
        this.updatedAt = now;
    }

    public void setLockedUntil(Instant lockedUntil, Instant now) {
        this.lockedUntil = lockedUntil;
        this.updatedAt = now;
    }

    public enum Role {
        CUSTOMER,
        ADMIN
    }
}
