CREATE TABLE event_consumer_failure
(
    topic           VARCHAR(249) NOT NULL,
    partition_id    INTEGER      NOT NULL,
    record_offset   BIGINT       NOT NULL,
    failure_kind    VARCHAR(16)  NOT NULL,
    state           VARCHAR(16)  NOT NULL,
    attempt_count   INTEGER      NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    last_error      VARCHAR(200) NOT NULL,
    event_id        UUID,
    correlation_id  UUID,
    first_failed_at TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    quarantined_at  TIMESTAMPTZ,
    CONSTRAINT event_consumer_failure_pkey PRIMARY KEY (topic, partition_id, record_offset),
    CONSTRAINT event_consumer_failure_kind_valid CHECK (failure_kind IN ('TRANSIENT', 'INVALID')),
    CONSTRAINT event_consumer_failure_state_valid CHECK (state IN ('RETRYING', 'QUARANTINED')),
    CONSTRAINT event_consumer_failure_attempts_positive CHECK (attempt_count > 0),
    CONSTRAINT event_consumer_failure_offset_nonnegative CHECK (record_offset >= 0),
    CONSTRAINT event_consumer_failure_state_shape CHECK (
        (state = 'RETRYING' AND next_attempt_at IS NOT NULL AND quarantined_at IS NULL)
        OR (state = 'QUARANTINED' AND next_attempt_at IS NULL AND quarantined_at IS NOT NULL)
    )
);

CREATE INDEX event_consumer_failure_quarantine_idx
    ON event_consumer_failure (quarantined_at)
    WHERE state = 'QUARANTINED';
