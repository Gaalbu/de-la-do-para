CREATE TABLE checkout_snapshots (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES carts (id) ON DELETE RESTRICT,
    cart_version BIGINT NOT NULL,
    guest_session_key VARCHAR(160) NOT NULL,
    items JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT checkout_snapshots_cart_version_nonnegative CHECK (cart_version >= 0)
);

CREATE INDEX checkout_snapshots_owner_idx ON checkout_snapshots (id, guest_session_key);
CREATE INDEX checkout_snapshots_cart_idx ON checkout_snapshots (cart_id, cart_version);
