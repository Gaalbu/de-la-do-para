CREATE TABLE identity_mail_outbox (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    token_id UUID NOT NULL REFERENCES verification_tokens(id) ON DELETE CASCADE,
    message_type VARCHAR(16) NOT NULL CHECK (message_type IN ('VERIFY', 'RECOVERY')),
    encrypted_payload TEXT,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'CANCELLED')),
    available_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    lease_until TIMESTAMPTZ,
    last_error_code VARCHAR(80),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT identity_mail_outbox_state CHECK (
        (status = 'PENDING' AND encrypted_payload IS NOT NULL AND sent_at IS NULL)
        OR (status = 'SENT' AND encrypted_payload IS NULL AND sent_at IS NOT NULL)
        OR (status = 'CANCELLED' AND encrypted_payload IS NULL AND sent_at IS NULL)
    )
);

CREATE INDEX identity_mail_outbox_due_idx
    ON identity_mail_outbox (available_at, created_at)
    WHERE status = 'PENDING';
