CREATE TABLE purchase_order
(
    id                    UUID         NOT NULL,
    checkout_key          VARCHAR(160) NOT NULL,
    account_id            UUID,
    contact_email         VARCHAR(254) NOT NULL,
    fulfillment_mode      VARCHAR(16)  NOT NULL,
    currency              CHAR(3)      NOT NULL,
    subtotal_cents        BIGINT       NOT NULL,
    shipping_cents        BIGINT       NOT NULL,
    discount_type         VARCHAR(16),
    discount_value        BIGINT,
    discount_cents        BIGINT       NOT NULL DEFAULT 0,
    coupon_code           VARCHAR(64),
    total_cents           BIGINT       NOT NULL,
    preparation_days      INTEGER      NOT NULL,
    delivery_days         INTEGER,
    pricing_rule_version  VARCHAR(32)  NOT NULL,
    destination           JSONB        NOT NULL,
    status                VARCHAR(24)  NOT NULL,
    status_sequence       INTEGER      NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT purchase_order_pkey PRIMARY KEY (id),
    CONSTRAINT purchase_order_checkout_key UNIQUE (checkout_key),
    CONSTRAINT purchase_order_mode_valid CHECK (fulfillment_mode IN ('DELIVERY', 'PICKUP')),
    CONSTRAINT purchase_order_currency_brl CHECK (currency = 'BRL'),
    CONSTRAINT purchase_order_amounts_valid CHECK (
        subtotal_cents >= 0 AND shipping_cents >= 0 AND discount_cents >= 0 AND total_cents >= 0
        AND discount_cents <= subtotal_cents
        AND total_cents = subtotal_cents - discount_cents + shipping_cents),
    CONSTRAINT purchase_order_status_valid CHECK (status IN (
        'PENDING_PAYMENT', 'PAID', 'PREPARING', 'READY_FOR_PICKUP', 'IN_TRANSIT',
        'DELIVERED', 'PICKED_UP', 'CANCELLED', 'EXPIRED', 'UNDER_REVIEW')),
    CONSTRAINT purchase_order_sequence_nonnegative CHECK (status_sequence >= 0),
    CONSTRAINT purchase_order_email_nonblank CHECK (btrim(contact_email) <> '')
);

CREATE INDEX purchase_order_account_idx ON purchase_order (account_id, created_at DESC) WHERE account_id IS NOT NULL;
CREATE INDEX purchase_order_status_idx ON purchase_order (status, updated_at);

CREATE TABLE purchase_order_item
(
    order_id          UUID         NOT NULL REFERENCES purchase_order (id),
    line_no           INTEGER      NOT NULL,
    sku_id            UUID         NOT NULL,
    product_name      VARCHAR(200) NOT NULL,
    sku_label         VARCHAR(200) NOT NULL,
    quantity          INTEGER      NOT NULL,
    unit_price_cents  BIGINT       NOT NULL,
    line_total_cents  BIGINT       NOT NULL,
    CONSTRAINT purchase_order_item_pkey PRIMARY KEY (order_id, line_no),
    CONSTRAINT purchase_order_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT purchase_order_item_total_valid CHECK (
        unit_price_cents >= 0 AND line_total_cents = unit_price_cents * quantity)
);

CREATE TABLE purchase_order_status_history
(
    order_id        UUID         NOT NULL REFERENCES purchase_order (id),
    sequence        INTEGER      NOT NULL,
    from_status     VARCHAR(24),
    to_status       VARCHAR(24)  NOT NULL,
    actor           VARCHAR(16)  NOT NULL,
    reason          VARCHAR(64),
    occurred_at     TIMESTAMPTZ  NOT NULL,
    correlation_id  UUID         NOT NULL,
    CONSTRAINT purchase_order_status_history_pkey PRIMARY KEY (order_id, sequence),
    CONSTRAINT purchase_order_history_actor_valid CHECK (actor IN ('SYSTEM', 'CUSTOMER', 'ADMIN')),
    CONSTRAINT purchase_order_history_sequence_nonnegative CHECK (sequence >= 0),
    CONSTRAINT purchase_order_history_first_shape CHECK ((sequence = 0) = (from_status IS NULL))
);

CREATE FUNCTION purchase_order_reject_change() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'purchase order records are immutable: % on %', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER purchase_order_item_immutable
    BEFORE UPDATE OR DELETE ON purchase_order_item
    FOR EACH ROW EXECUTE FUNCTION purchase_order_reject_change();

CREATE TRIGGER purchase_order_history_immutable
    BEFORE UPDATE OR DELETE ON purchase_order_status_history
    FOR EACH ROW EXECUTE FUNCTION purchase_order_reject_change();

CREATE FUNCTION purchase_order_guard_snapshot() RETURNS trigger AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'purchase orders cannot be deleted' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF (NEW.id, NEW.checkout_key, NEW.account_id, NEW.contact_email, NEW.fulfillment_mode, NEW.currency,
        NEW.subtotal_cents, NEW.shipping_cents, NEW.discount_type, NEW.discount_value, NEW.discount_cents,
        NEW.coupon_code, NEW.total_cents, NEW.preparation_days, NEW.delivery_days, NEW.pricing_rule_version,
        NEW.destination, NEW.created_at)
        IS DISTINCT FROM
       (OLD.id, OLD.checkout_key, OLD.account_id, OLD.contact_email, OLD.fulfillment_mode, OLD.currency,
        OLD.subtotal_cents, OLD.shipping_cents, OLD.discount_type, OLD.discount_value, OLD.discount_cents,
        OLD.coupon_code, OLD.total_cents, OLD.preparation_days, OLD.delivery_days, OLD.pricing_rule_version,
        OLD.destination, OLD.created_at) THEN
        RAISE EXCEPTION 'purchase order snapshot is immutable' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER purchase_order_snapshot_guard
    BEFORE UPDATE OR DELETE ON purchase_order
    FOR EACH ROW EXECUTE FUNCTION purchase_order_guard_snapshot();
