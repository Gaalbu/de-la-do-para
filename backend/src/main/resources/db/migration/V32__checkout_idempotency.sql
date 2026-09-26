CREATE TABLE checkout_idempotency
(
    subject       VARCHAR(200) NOT NULL,
    operation     VARCHAR(40)  NOT NULL,
    idem_key      VARCHAR(160) NOT NULL,
    request_hash  CHAR(64)     NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    order_id      UUID,
    created_at    TIMESTAMPTZ  NOT NULL,
    completed_at  TIMESTAMPTZ,
    CONSTRAINT checkout_idempotency_pkey PRIMARY KEY (subject, operation, idem_key),
    CONSTRAINT checkout_idempotency_status_valid CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT checkout_idempotency_result_shape CHECK (
        (status = 'COMPLETED') = (order_id IS NOT NULL AND completed_at IS NOT NULL))
);
