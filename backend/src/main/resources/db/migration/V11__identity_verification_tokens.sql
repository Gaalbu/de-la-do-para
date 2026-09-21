-- V11 — identity: tokens de verificação e recuperação (SPEC-identity §7.2)
CREATE TABLE verification_tokens (
    id          UUID            PRIMARY KEY,
    account_id  UUID            NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)     NOT NULL UNIQUE,
    type        VARCHAR(20)     NOT NULL CHECK (type IN ('VERIFY', 'RECOVERY')),
    expires_at  TIMESTAMPTZ     NOT NULL,
    used_at     TIMESTAMPTZ     NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_verification_tokens_account ON verification_tokens(account_id);
CREATE INDEX ix_verification_tokens_expires ON verification_tokens(expires_at);
