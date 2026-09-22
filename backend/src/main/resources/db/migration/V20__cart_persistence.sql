CREATE TABLE carts (
    id UUID PRIMARY KEY,
    guest_session_key VARCHAR(160),
    account_id UUID REFERENCES accounts (id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT carts_one_owner CHECK ((guest_session_key IS NOT NULL) <> (account_id IS NOT NULL)),
    CONSTRAINT carts_status_valid CHECK (status IN ('OPEN', 'CHECKOUT_STARTED')),
    CONSTRAINT carts_version_nonnegative CHECK (version >= 0)
);

CREATE UNIQUE INDEX carts_guest_session_unique ON carts (guest_session_key) WHERE guest_session_key IS NOT NULL;
CREATE UNIQUE INDEX carts_account_unique ON carts (account_id) WHERE account_id IS NOT NULL;

CREATE TABLE cart_items (
    cart_id UUID NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    sku_id UUID NOT NULL REFERENCES product_skus (id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (cart_id, sku_id),
    CONSTRAINT cart_items_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX cart_items_sku_idx ON cart_items (sku_id);
