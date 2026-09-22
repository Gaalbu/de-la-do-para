CREATE TABLE shipping_quotes (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL,
    snapshot_version BIGINT NOT NULL,
    destination_postal_code VARCHAR(8) NOT NULL,
    input_fingerprint VARCHAR(128) NOT NULL,
    service_id VARCHAR(80) NOT NULL,
    service_name VARCHAR(160) NOT NULL,
    price_cents BIGINT NOT NULL,
    delivery_days INTEGER NOT NULL,
    preparation_days INTEGER NOT NULL,
    package_sequences JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT shipping_quotes_snapshot_version_nonnegative CHECK (snapshot_version >= 0),
    CONSTRAINT shipping_quotes_price_nonnegative CHECK (price_cents >= 0),
    CONSTRAINT shipping_quotes_delivery_days_positive CHECK (delivery_days > 0),
    CONSTRAINT shipping_quotes_preparation_days_nonnegative CHECK (preparation_days >= 0),
    CONSTRAINT shipping_quotes_expiry_after_creation CHECK (expires_at > created_at)
);

CREATE INDEX shipping_quotes_snapshot_idx ON shipping_quotes (snapshot_id, snapshot_version);
CREATE INDEX shipping_quotes_expiry_idx ON shipping_quotes (expires_at);
