CREATE TABLE products (
    id UUID PRIMARY KEY,
    slug VARCHAR(80) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    category VARCHAR(16) NOT NULL,
    producer_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT products_producer_fk FOREIGN KEY (producer_id) REFERENCES producers (id) ON DELETE RESTRICT,
    CONSTRAINT products_category_valid CHECK (category IN ('FOOD', 'CRAFT')),
    CONSTRAINT products_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT products_slug_nonblank CHECK (btrim(slug) <> ''),
    CONSTRAINT products_name_nonblank CHECK (btrim(display_name) <> ''),
    CONSTRAINT products_description_nonblank CHECK (btrim(description) <> ''),
    CONSTRAINT products_id_category_unique UNIQUE (id, category)
);

CREATE UNIQUE INDEX products_slug_lower_unique ON products (LOWER(slug));
CREATE INDEX products_producer_idx ON products (producer_id);

CREATE TABLE product_skus (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    product_category VARCHAR(16) NOT NULL,
    sku_code VARCHAR(80) NOT NULL,
    sales_unit VARCHAR(40) NOT NULL,
    net_content_grams INTEGER,
    minimum_shelf_life_days INTEGER,
    fragile BOOLEAN NOT NULL DEFAULT FALSE,
    length_mm INTEGER NOT NULL,
    width_mm INTEGER NOT NULL,
    height_mm INTEGER NOT NULL,
    gross_weight_grams INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT product_skus_product_fk FOREIGN KEY (product_id, product_category)
        REFERENCES products (id, category) ON DELETE RESTRICT,
    CONSTRAINT product_skus_category_valid CHECK (product_category IN ('FOOD', 'CRAFT')),
    CONSTRAINT product_skus_code_format CHECK (sku_code ~ '^[A-Z0-9]+(-[A-Z0-9]+)*$'),
    CONSTRAINT product_skus_sales_unit_nonblank CHECK (btrim(sales_unit) <> ''),
    CONSTRAINT product_skus_category_requirements CHECK (
        (product_category = 'FOOD' AND net_content_grams > 0 AND minimum_shelf_life_days > 0 AND fragile = FALSE)
        OR (product_category = 'CRAFT' AND net_content_grams IS NULL AND minimum_shelf_life_days IS NULL)
    ),
    CONSTRAINT product_skus_dimensions_positive CHECK (length_mm > 0 AND width_mm > 0 AND height_mm > 0),
    CONSTRAINT product_skus_weight_positive CHECK (gross_weight_grams > 0)
);

CREATE UNIQUE INDEX product_skus_code_lower_unique ON product_skus (LOWER(sku_code));
CREATE INDEX product_skus_product_idx ON product_skus (product_id);
