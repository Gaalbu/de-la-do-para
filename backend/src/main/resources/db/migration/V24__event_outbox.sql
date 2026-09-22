CREATE TABLE event_outbox
(
    event_id          UUID         NOT NULL,
    event_type        VARCHAR(160) NOT NULL,
    schema_version    INTEGER      NOT NULL,
    aggregate_id      VARCHAR(160) NOT NULL,
    aggregate_version BIGINT       NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    correlation_id    UUID         NOT NULL,
    causation_id      UUID         NOT NULL,
    payload           JSONB        NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    available_at      TIMESTAMPTZ  NOT NULL,
    attempt_count     INTEGER      NOT NULL DEFAULT 0,
    lease_until       TIMESTAMPTZ,
    last_error        TEXT,
    published_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT event_outbox_pkey PRIMARY KEY (event_id),
    CONSTRAINT event_outbox_schema_version_positive CHECK (schema_version > 0),
    CONSTRAINT event_outbox_aggregate_version_nonnegative CHECK (aggregate_version >= 0),
    CONSTRAINT event_outbox_attempt_count_nonnegative CHECK (attempt_count >= 0),
    CONSTRAINT event_outbox_status_valid CHECK (status IN ('PENDING', 'PUBLISHED', 'QUARANTINED')),
    CONSTRAINT event_outbox_event_type_nonblank CHECK (btrim(event_type) <> ''),
    CONSTRAINT event_outbox_aggregate_id_nonblank CHECK (btrim(aggregate_id) <> '')
);

CREATE INDEX event_outbox_pending_idx
    ON event_outbox (available_at, created_at)
    WHERE status = 'PENDING';
