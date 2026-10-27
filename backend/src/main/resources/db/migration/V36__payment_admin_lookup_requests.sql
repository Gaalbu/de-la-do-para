-- C82a: an administrator may ask for one more provider lookup of an uncertain payment.
-- The request only enqueues a QUERY operation (worker-owned); it never creates a charge or refund.
CREATE TABLE payment_admin_lookup_request
(
    id           UUID         NOT NULL,
    intent_id    UUID         NOT NULL REFERENCES payment_intent (id),
    operation_id UUID         NOT NULL REFERENCES payment_external_operation (id),
    actor        VARCHAR(320) NOT NULL,
    reason       VARCHAR(200) NOT NULL,
    requested_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT payment_admin_lookup_request_pkey PRIMARY KEY (id),
    CONSTRAINT payment_admin_lookup_request_operation_unique UNIQUE (operation_id),
    CONSTRAINT payment_admin_lookup_request_reason_nonblank CHECK (btrim(reason) <> ''),
    CONSTRAINT payment_admin_lookup_request_actor_nonblank CHECK (btrim(actor) <> '')
);

CREATE INDEX payment_admin_lookup_request_intent_idx ON payment_admin_lookup_request (intent_id, requested_at);

-- At most one lookup waiting to be claimed per intent: a repeated command reuses it.
CREATE UNIQUE INDEX payment_operation_one_pending_query ON payment_external_operation (intent_id)
    WHERE kind = 'QUERY' AND status = 'PENDING';

CREATE FUNCTION payment_admin_lookup_request_reject_change() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'payment administrator requests are an audit trail: % is not allowed', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_admin_lookup_request_immutable
    BEFORE UPDATE OR DELETE ON payment_admin_lookup_request
    FOR EACH ROW EXECUTE FUNCTION payment_admin_lookup_request_reject_change();
