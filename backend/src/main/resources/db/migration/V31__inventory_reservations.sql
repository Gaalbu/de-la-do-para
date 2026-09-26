CREATE TABLE inventory_reservation
(
    id          UUID         NOT NULL,
    reference   VARCHAR(160) NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT inventory_reservation_pkey PRIMARY KEY (id),
    CONSTRAINT inventory_reservation_reference_unique UNIQUE (reference),
    CONSTRAINT inventory_reservation_status_valid CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED')),
    CONSTRAINT inventory_reservation_hold_15_minutes CHECK (expires_at = created_at + interval '15 minutes')
);

CREATE INDEX inventory_reservation_active_expiry_idx ON inventory_reservation (expires_at) WHERE status = 'ACTIVE';

CREATE TABLE inventory_reservation_line
(
    reservation_id UUID    NOT NULL REFERENCES inventory_reservation (id),
    lot_id         UUID    NOT NULL REFERENCES inventory_lots (id),
    sku_id         UUID    NOT NULL,
    units          INTEGER NOT NULL,
    CONSTRAINT inventory_reservation_line_pkey PRIMARY KEY (reservation_id, lot_id),
    CONSTRAINT inventory_reservation_line_units_positive CHECK (units > 0)
);
