CREATE TABLE payment_intent
(
    id                  UUID         NOT NULL,
    order_id            UUID         NOT NULL,
    amount_cents        BIGINT       NOT NULL,
    currency            CHAR(3)      NOT NULL,
    status              VARCHAR(24)  NOT NULL,
    status_version      INTEGER      NOT NULL,
    status_reason       VARCHAR(64),
    provider_checkout_id VARCHAR(100),
    checkout_url        VARCHAR(500),
    checkout_expires_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT payment_intent_pkey PRIMARY KEY (id),
    CONSTRAINT payment_intent_order_unique UNIQUE (order_id),
    CONSTRAINT payment_intent_amount_positive CHECK (amount_cents > 0),
    CONSTRAINT payment_intent_currency_brl CHECK (currency = 'BRL'),
    CONSTRAINT payment_intent_version_nonnegative CHECK (status_version >= 0),
    CONSTRAINT payment_intent_status_valid CHECK (status IN (
        'REQUESTED', 'CREATING_CHECKOUT', 'AWAITING_PAYMENT', 'CONFIRMED', 'DECLINED', 'UNKNOWN',
        'UNDER_REVIEW', 'REFUND_REQUESTED', 'REFUNDED'))
);

CREATE FUNCTION payment_intent_guard_reference() RETURNS trigger AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'payment intents cannot be deleted' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF (NEW.id, NEW.order_id, NEW.amount_cents, NEW.currency, NEW.created_at)
        IS DISTINCT FROM (OLD.id, OLD.order_id, OLD.amount_cents, OLD.currency, OLD.created_at) THEN
        RAISE EXCEPTION 'payment intent reference and amount are immutable'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_intent_reference_guard
    BEFORE UPDATE OR DELETE ON payment_intent
    FOR EACH ROW EXECUTE FUNCTION payment_intent_guard_reference();

CREATE TABLE payment_external_operation
(
    id            UUID         NOT NULL,
    intent_id     UUID         NOT NULL REFERENCES payment_intent (id),
    kind          VARCHAR(24)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    lease_until   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    last_error    VARCHAR(200),
    CONSTRAINT payment_external_operation_pkey PRIMARY KEY (id),
    CONSTRAINT payment_operation_kind_valid CHECK (kind IN ('CREATE_CHECKOUT', 'REFUND', 'QUERY')),
    CONSTRAINT payment_operation_status_valid CHECK (status IN (
        'PENDING', 'IN_FLIGHT', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    CONSTRAINT payment_operation_in_flight_leased CHECK ((status = 'IN_FLIGHT') = (lease_until IS NOT NULL))
);

CREATE UNIQUE INDEX payment_operation_one_checkout ON payment_external_operation (intent_id)
    WHERE kind = 'CREATE_CHECKOUT';
CREATE UNIQUE INDEX payment_operation_one_refund ON payment_external_operation (intent_id)
    WHERE kind = 'REFUND';
CREATE INDEX payment_operation_pending_idx ON payment_external_operation (created_at) WHERE status = 'PENDING';
CREATE INDEX payment_operation_in_flight_idx ON payment_external_operation (lease_until) WHERE status = 'IN_FLIGHT';
