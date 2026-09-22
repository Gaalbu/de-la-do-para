CREATE TABLE product_images (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL UNIQUE REFERENCES products(id) ON DELETE RESTRICT,
    storage_key UUID NOT NULL UNIQUE,
    content_type VARCHAR(32) NOT NULL,
    extension VARCHAR(8) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    alt_text VARCHAR(250) NOT NULL,
    source VARCHAR(1500) NOT NULL,
    license VARCHAR(200) NOT NULL,
    creator VARCHAR(200) NOT NULL,
    attribution VARCHAR(500) NOT NULL,
    rights_reviewed BOOLEAN NOT NULL CHECK (rights_reviewed),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT product_images_format_matches CHECK (
        (content_type = 'image/jpeg' AND extension = 'jpg') OR
        (content_type = 'image/png' AND extension = 'png')
    ),
    CONSTRAINT product_images_dimensions_valid CHECK (
        width > 0 AND height > 0 AND width <= 6000 AND height <= 6000 AND width::BIGINT * height <= 12000000
    )
);
