CREATE TABLE event_consumption
(
    event_id           UUID         NOT NULL,
    handler_name       VARCHAR(160) NOT NULL,
    event_type         VARCHAR(160) NOT NULL,
    schema_version     INTEGER      NOT NULL,
    aggregate_id       VARCHAR(160) NOT NULL,
    aggregate_version  BIGINT       NOT NULL,
    occurred_at        TIMESTAMPTZ  NOT NULL,
    correlation_id     UUID         NOT NULL,
    payload            JSONB        NOT NULL,
    processing_result  VARCHAR(24)  NOT NULL DEFAULT 'PENDING_ORDER',
    received_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_at         TIMESTAMPTZ,
    CONSTRAINT event_consumption_pkey PRIMARY KEY (event_id, handler_name),
    CONSTRAINT event_consumption_schema_version_positive CHECK (schema_version > 0),
    CONSTRAINT event_consumption_aggregate_version_nonnegative CHECK (aggregate_version >= 0),
    CONSTRAINT event_consumption_result_valid CHECK (processing_result IN ('PENDING_ORDER', 'APPLIED')),
    CONSTRAINT event_consumption_applied_at_valid CHECK (
        (processing_result = 'APPLIED' AND applied_at IS NOT NULL)
        OR (processing_result = 'PENDING_ORDER' AND applied_at IS NULL)
    ),
    CONSTRAINT event_consumption_handler_nonblank CHECK (btrim(handler_name) <> ''),
    CONSTRAINT event_consumption_event_type_nonblank CHECK (btrim(event_type) <> ''),
    CONSTRAINT event_consumption_aggregate_id_nonblank CHECK (btrim(aggregate_id) <> '')
);

CREATE INDEX event_consumption_pending_order_idx
    ON event_consumption (handler_name, aggregate_id, aggregate_version)
    WHERE processing_result = 'PENDING_ORDER';

CREATE TABLE event_consumer_cursor
(
    handler_name           VARCHAR(160) NOT NULL,
    aggregate_id           VARCHAR(160) NOT NULL,
    last_aggregate_version BIGINT       NOT NULL DEFAULT -1,
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT event_consumer_cursor_pkey PRIMARY KEY (handler_name, aggregate_id),
    CONSTRAINT event_consumer_cursor_version_valid CHECK (last_aggregate_version >= -1),
    CONSTRAINT event_consumer_cursor_handler_nonblank CHECK (btrim(handler_name) <> ''),
    CONSTRAINT event_consumer_cursor_aggregate_nonblank CHECK (btrim(aggregate_id) <> '')
);
