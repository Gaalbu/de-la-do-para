CREATE TABLE shipping_tracking_event
(
    payload_hash          CHAR(64)     NOT NULL,
    provider_shipment_id VARCHAR(160) NOT NULL,
    event_type           VARCHAR(32)  NOT NULL,
    progress             VARCHAR(24)  NOT NULL,
    exception            BOOLEAN      NOT NULL,
    occurred_at          TIMESTAMPTZ  NOT NULL,
    received_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT shipping_tracking_event_pkey PRIMARY KEY (payload_hash),
    CONSTRAINT shipping_tracking_event_progress_valid CHECK
        (progress IN ('LABEL_CREATED', 'HANDED_TO_CARRIER', 'IN_TRANSIT', 'DELIVERED', 'UNDER_REVIEW', 'CANCELLED'))
);

CREATE INDEX shipping_tracking_event_shipment_idx
    ON shipping_tracking_event (provider_shipment_id, occurred_at DESC);

CREATE TABLE shipping_package_tracking
(
    provider_shipment_id VARCHAR(160) NOT NULL,
    order_id              UUID         NOT NULL REFERENCES purchase_order (id) ON DELETE RESTRICT,
    shipment_unit_id      UUID         NOT NULL,
    package_sequences     JSONB        NOT NULL,
    event_type            VARCHAR(32)  NOT NULL,
    progress              VARCHAR(24)  NOT NULL,
    exception             BOOLEAN      NOT NULL,
    occurred_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT shipping_package_tracking_pkey PRIMARY KEY (provider_shipment_id),
    CONSTRAINT shipping_package_tracking_packages_valid CHECK
        (jsonb_typeof(package_sequences) = 'array' AND jsonb_array_length(package_sequences) > 0),
    CONSTRAINT shipping_package_tracking_progress_valid CHECK
        (progress IN ('LABEL_CREATED', 'HANDED_TO_CARRIER', 'IN_TRANSIT', 'DELIVERED', 'UNDER_REVIEW', 'CANCELLED'))
);
