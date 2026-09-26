CREATE TABLE payment_provider_event
(
    provider          VARCHAR(16)  NOT NULL,
    event_id          VARCHAR(120) NOT NULL,
    event_type        VARCHAR(40)  NOT NULL,
    checkout_id       VARCHAR(100),
    checkout_status   VARCHAR(24),
    provider_created  VARCHAR(40),
    status            VARCHAR(16)  NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL,
    processed_at      TIMESTAMPTZ,
    CONSTRAINT payment_provider_event_pkey PRIMARY KEY (provider, event_id),
    CONSTRAINT payment_provider_event_status_valid CHECK (status IN ('RECEIVED', 'PROCESSED', 'IGNORED')),
    CONSTRAINT payment_provider_event_id_nonblank CHECK (btrim(event_id) <> '')
);

CREATE INDEX payment_provider_event_received_idx ON payment_provider_event (received_at) WHERE status = 'RECEIVED';
