CREATE TABLE coupon
(
    id                UUID         NOT NULL,
    code_normalized   VARCHAR(64)  NOT NULL,
    discount_type     VARCHAR(16)  NOT NULL,
    discount_value    BIGINT       NOT NULL,
    minimum_cents     BIGINT       NOT NULL DEFAULT 0,
    valid_from        TIMESTAMPTZ  NOT NULL,
    valid_until       TIMESTAMPTZ  NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    global_limit      INTEGER,
    per_email_limit   INTEGER      NOT NULL DEFAULT 1,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT coupon_pkey PRIMARY KEY (id),
    CONSTRAINT coupon_code_key UNIQUE (code_normalized),
    CONSTRAINT coupon_code_shape CHECK (code_normalized = upper(btrim(code_normalized)) AND code_normalized <> ''),
    CONSTRAINT coupon_type_valid CHECK (discount_type IN ('PERCENTAGE', 'FIXED')),
    CONSTRAINT coupon_value_valid CHECK (
        discount_value > 0 AND (discount_type = 'FIXED' OR discount_value <= 100)
    ),
    CONSTRAINT coupon_minimum_nonnegative CHECK (minimum_cents >= 0),
    CONSTRAINT coupon_validity_ordered CHECK (valid_until > valid_from),
    CONSTRAINT coupon_global_limit_positive CHECK (global_limit IS NULL OR global_limit > 0),
    CONSTRAINT coupon_email_limit_positive CHECK (per_email_limit > 0)
);

CREATE TABLE coupon_usage
(
    id                UUID         NOT NULL,
    coupon_id         UUID         NOT NULL,
    email_normalized  VARCHAR(254) NOT NULL,
    reservation_key   VARCHAR(160) NOT NULL,
    state             VARCHAR(16)  NOT NULL DEFAULT 'RESERVED',
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT coupon_usage_pkey PRIMARY KEY (id),
    CONSTRAINT coupon_usage_coupon_fkey FOREIGN KEY (coupon_id) REFERENCES coupon (id),
    CONSTRAINT coupon_usage_reservation_key UNIQUE (reservation_key),
    CONSTRAINT coupon_usage_state_valid CHECK (state IN ('RESERVED', 'CONSUMED', 'RELEASED', 'REFUNDED')),
    CONSTRAINT coupon_usage_email_nonblank CHECK (btrim(email_normalized) <> '')
);

CREATE INDEX coupon_usage_coupon_email_idx ON coupon_usage (coupon_id, email_normalized);
