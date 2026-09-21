-- V10 — identity: contas opcionais (D64, SPEC-identity §7.2)
CREATE TABLE accounts (
    id              UUID            PRIMARY KEY,
    email           VARCHAR(254)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    email_verified  BOOLEAN         NOT NULL DEFAULT FALSE,
    role            VARCHAR(20)     NOT NULL
        CHECK (role IN ('CUSTOMER', 'ADMIN')),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    locked_until    TIMESTAMPTZ     NULL,
    CONSTRAINT chk_accounts_email_length CHECK (char_length(email) <= 254)
);

CREATE UNIQUE INDEX ux_accounts_email_lower ON accounts (LOWER(email));
