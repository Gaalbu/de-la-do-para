CREATE TABLE pricing_sku_prices (
    sku_id UUID PRIMARY KEY,
    unit_price_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pricing_sku_prices_sku_fk FOREIGN KEY (sku_id)
        REFERENCES product_skus (id) ON DELETE RESTRICT,
    CONSTRAINT pricing_sku_prices_amount_positive CHECK (unit_price_cents > 0),
    CONSTRAINT pricing_sku_prices_currency_brl CHECK (currency = 'BRL')
);
