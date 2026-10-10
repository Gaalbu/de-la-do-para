ALTER TABLE shipping_quotes
    ADD COLUMN package_manifest JSONB;

ALTER TABLE shipping_quotes
    ADD CONSTRAINT shipping_quotes_package_manifest_shape_valid
        CHECK (package_manifest IS NULL OR (
            jsonb_typeof(package_manifest) = 'array'
            AND jsonb_array_length(package_manifest) > 0
        ));
