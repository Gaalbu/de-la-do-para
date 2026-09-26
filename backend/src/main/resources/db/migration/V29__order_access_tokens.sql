CREATE TABLE purchase_order_access_token
(
    token_hash  CHAR(64)    NOT NULL,
    order_id    UUID        NOT NULL REFERENCES purchase_order (id),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT purchase_order_access_token_pkey PRIMARY KEY (token_hash)
);

CREATE INDEX purchase_order_access_token_order_idx ON purchase_order_access_token (order_id);

CREATE TRIGGER purchase_order_access_token_immutable
    BEFORE UPDATE OR DELETE ON purchase_order_access_token
    FOR EACH ROW EXECUTE FUNCTION purchase_order_reject_change();
