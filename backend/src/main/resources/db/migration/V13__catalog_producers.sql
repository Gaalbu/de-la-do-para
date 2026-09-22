CREATE TABLE producers (
    id UUID PRIMARY KEY,
    slug VARCHAR(80) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    origin_label VARCHAR(300) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT producers_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT producers_display_name_nonblank CHECK (btrim(display_name) <> ''),
    CONSTRAINT producers_origin_label_nonblank CHECK (btrim(origin_label) <> ''),
    CONSTRAINT producers_description_nonblank CHECK (btrim(description) <> '')
);

CREATE UNIQUE INDEX producers_slug_lower_unique ON producers (LOWER(slug));
