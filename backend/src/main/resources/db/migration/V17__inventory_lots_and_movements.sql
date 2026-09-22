CREATE TABLE inventory_lots (
    id UUID PRIMARY KEY,
    sku_id UUID NOT NULL,
    physical_units INTEGER NOT NULL DEFAULT 0,
    reserved_units INTEGER NOT NULL DEFAULT 0,
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    expires_on DATE,
    minimum_shelf_life_days INTEGER,
    received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT inventory_lots_sku_fk FOREIGN KEY (sku_id)
        REFERENCES product_skus (id) ON DELETE RESTRICT,
    CONSTRAINT inventory_lots_balance_valid CHECK (
        physical_units >= 0 AND reserved_units >= 0 AND reserved_units <= physical_units
    ),
    CONSTRAINT inventory_lots_validity_pair CHECK (
        (expires_on IS NULL AND minimum_shelf_life_days IS NULL)
        OR (expires_on IS NOT NULL AND minimum_shelf_life_days >= 0)
    )
);

CREATE INDEX inventory_lots_sku_idx ON inventory_lots (sku_id);
CREATE INDEX inventory_lots_availability_idx
    ON inventory_lots (sku_id, blocked, expires_on);

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,
    sku_id UUID NOT NULL,
    movement_type VARCHAR(32) NOT NULL,
    units_delta INTEGER NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    actor_id UUID,
    reason VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT inventory_movements_sku_fk FOREIGN KEY (sku_id)
        REFERENCES product_skus (id) ON DELETE RESTRICT,
    CONSTRAINT inventory_movements_delta_nonzero CHECK (units_delta <> 0),
    CONSTRAINT inventory_movements_type_valid CHECK (
        movement_type IN ('RECEIPT', 'RESERVATION', 'RELEASE', 'HANDOFF', 'ADMIN_ADJUSTMENT')
    ),
    CONSTRAINT inventory_movements_key_nonblank CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT inventory_movements_reason_nonblank CHECK (btrim(reason) <> '')
);

CREATE UNIQUE INDEX inventory_movements_idempotency_unique
    ON inventory_movements (idempotency_key);
CREATE INDEX inventory_movements_sku_time_idx
    ON inventory_movements (sku_id, occurred_at);
