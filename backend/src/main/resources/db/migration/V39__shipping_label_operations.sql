CREATE TABLE shipping_label_operation
(
    id                    UUID         NOT NULL,
    order_id              UUID         NOT NULL REFERENCES purchase_order (id) ON DELETE RESTRICT,
    shipment_unit_id      UUID         NOT NULL,
    package_sequences    JSONB        NOT NULL,
    step                  VARCHAR(24)  NOT NULL,
    state                 VARCHAR(16)  NOT NULL,
    correlation_id        UUID         NOT NULL,
    provider_shipment_id VARCHAR(160),
    failure_code          VARCHAR(80),
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    requested_at          TIMESTAMPTZ,
    resolved_at           TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT shipping_label_operation_pkey PRIMARY KEY (id),
    CONSTRAINT shipping_label_operation_step_valid CHECK (step IN ('ADD_TO_CART', 'PURCHASE', 'GENERATE')),
    CONSTRAINT shipping_label_operation_followup_id_valid CHECK (
        step = 'ADD_TO_CART' OR provider_shipment_id IS NOT NULL),
    CONSTRAINT shipping_label_operation_state_valid
        CHECK (state IN ('READY', 'REQUESTED', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    CONSTRAINT shipping_label_operation_packages_valid CHECK (
        jsonb_typeof(package_sequences) = 'array' AND jsonb_array_length(package_sequences) > 0),
    CONSTRAINT shipping_label_operation_failure_code_valid CHECK (
        failure_code IS NULL OR failure_code ~ '^[A-Z][A-Z0-9_]{0,79}$'),
    CONSTRAINT shipping_label_operation_state_shape CHECK (
        (state = 'READY' AND requested_at IS NULL AND resolved_at IS NULL AND failure_code IS NULL)
        OR (state = 'REQUESTED' AND requested_at IS NOT NULL AND resolved_at IS NULL AND failure_code IS NULL)
        OR (state IN ('SUCCEEDED', 'UNKNOWN') AND requested_at IS NOT NULL AND resolved_at IS NOT NULL
            AND failure_code IS NULL)
        OR (state = 'FAILED' AND requested_at IS NOT NULL AND resolved_at IS NOT NULL AND failure_code IS NOT NULL)),
    CONSTRAINT shipping_label_operation_timestamps_valid CHECK (
        updated_at >= created_at AND (requested_at IS NULL OR requested_at >= created_at)
        AND (resolved_at IS NULL OR resolved_at >= requested_at AND resolved_at <= updated_at)),
    CONSTRAINT shipping_label_operation_idempotency UNIQUE (order_id, shipment_unit_id, step)
);

CREATE INDEX shipping_label_operation_order_idx ON shipping_label_operation (order_id, created_at);
