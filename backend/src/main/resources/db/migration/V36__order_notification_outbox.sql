CREATE TABLE order_notification_outbox (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    template_version INTEGER NOT NULL,
    recipient VARCHAR(254) NOT NULL,
    order_id UUID NOT NULL REFERENCES purchase_order(id),
    event_type VARCHAR(64) NOT NULL,
    aggregate_version BIGINT NOT NULL CHECK (aggregate_version >= 0),
    order_status VARCHAR(32) NOT NULL,
    fulfillment_mode VARCHAR(16) NOT NULL,
    total_cents BIGINT NOT NULL CHECK (total_cents >= 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'CANCELLED', 'FAILED', 'UNKNOWN')),
    available_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    lease_until TIMESTAMPTZ,
    last_error_code VARCHAR(80),
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT order_notification_dedupe UNIQUE (event_id, template_version, recipient),
    CONSTRAINT order_notification_state CHECK (
        (status = 'PENDING' AND accepted_at IS NULL)
        OR (status = 'ACCEPTED' AND accepted_at IS NOT NULL)
        OR (status IN ('CANCELLED', 'FAILED', 'UNKNOWN') AND accepted_at IS NULL)
    )
);

CREATE INDEX order_notification_due_idx
    ON order_notification_outbox (available_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX order_notification_order_version_idx
    ON order_notification_outbox (order_id, aggregate_version, status);
