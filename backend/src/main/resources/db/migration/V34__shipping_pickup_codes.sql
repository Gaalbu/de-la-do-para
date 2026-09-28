CREATE TABLE shipping_pickup_code (
    order_id UUID PRIMARY KEY REFERENCES purchase_order(id) ON DELETE RESTRICT,
    pickup_status TEXT NOT NULL,
    ciphertext TEXT,
    issued_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CONSTRAINT shipping_pickup_code_status_valid CHECK (pickup_status IN ('READY_FOR_PICKUP', 'PICKED_UP')),
    CONSTRAINT shipping_pickup_code_state_valid CHECK (
        (pickup_status = 'READY_FOR_PICKUP' AND used_at IS NULL AND ciphertext IS NOT NULL)
        OR (pickup_status = 'PICKED_UP' AND used_at IS NOT NULL AND ciphertext IS NULL)
    )
);

CREATE INDEX shipping_pickup_code_issued_at_idx ON shipping_pickup_code (issued_at);
