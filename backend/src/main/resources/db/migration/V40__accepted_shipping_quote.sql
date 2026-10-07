ALTER TABLE purchase_order
    ADD COLUMN accepted_shipping_quote JSONB,
    ADD CONSTRAINT purchase_order_shipping_quote_mode_valid
        CHECK (accepted_shipping_quote IS NULL OR fulfillment_mode = 'DELIVERY'),
    ADD CONSTRAINT purchase_order_shipping_quote_shape_valid
        CHECK (accepted_shipping_quote IS NULL OR (
            jsonb_typeof(accepted_shipping_quote) = 'object'
            AND accepted_shipping_quote ? 'snapshotId'
            AND accepted_shipping_quote ? 'snapshotVersion'
            AND accepted_shipping_quote ? 'quoteId'
            AND accepted_shipping_quote ? 'inputFingerprint'
            AND accepted_shipping_quote ? 'serviceId'
            AND accepted_shipping_quote ? 'serviceName'
            AND accepted_shipping_quote ? 'packageSequences'
            AND jsonb_typeof(accepted_shipping_quote->'packageSequences') = 'array'
            AND jsonb_array_length(accepted_shipping_quote->'packageSequences') > 0
        ));

CREATE FUNCTION purchase_order_accepted_shipping_quote_immutable() RETURNS trigger AS
$$
BEGIN
    IF OLD.accepted_shipping_quote IS DISTINCT FROM NEW.accepted_shipping_quote THEN
        RAISE EXCEPTION 'accepted shipping quote is immutable' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER purchase_order_accepted_shipping_quote_immutable
    BEFORE UPDATE OF accepted_shipping_quote ON purchase_order
    FOR EACH ROW EXECUTE FUNCTION purchase_order_accepted_shipping_quote_immutable();
