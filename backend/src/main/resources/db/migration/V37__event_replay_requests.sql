-- C82: an administrator may ask for one quarantined record to be consumed again.
-- The replay re-reads the original envelope from the outbox, so the event keeps its identity and the
-- handlers apply their usual rules; the request is the audit trail and is never edited after it finishes.
CREATE TABLE event_replay_request
(
    id            UUID         NOT NULL,
    topic         VARCHAR(249) NOT NULL,
    partition_id  INTEGER      NOT NULL,
    record_offset BIGINT       NOT NULL,
    event_id      UUID         NOT NULL,
    actor         VARCHAR(320) NOT NULL,
    reason        VARCHAR(200) NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    result        VARCHAR(200),
    requested_at  TIMESTAMPTZ  NOT NULL,
    finished_at   TIMESTAMPTZ,
    CONSTRAINT event_replay_request_pkey PRIMARY KEY (id),
    CONSTRAINT event_replay_request_failure_fk FOREIGN KEY (topic, partition_id, record_offset)
        REFERENCES event_consumer_failure (topic, partition_id, record_offset),
    CONSTRAINT event_replay_request_status_valid CHECK (status IN ('PENDING', 'APPLIED', 'DUPLICATE', 'FAILED')),
    CONSTRAINT event_replay_request_finished_shape CHECK ((status = 'PENDING') = (finished_at IS NULL)),
    CONSTRAINT event_replay_request_reason_nonblank CHECK (btrim(reason) <> ''),
    CONSTRAINT event_replay_request_actor_nonblank CHECK (btrim(actor) <> '')
);

-- At most one replay waiting per record: a repeated command reuses it.
CREATE UNIQUE INDEX event_replay_request_one_pending ON event_replay_request (topic, partition_id, record_offset)
    WHERE status = 'PENDING';
CREATE INDEX event_replay_request_pending_idx ON event_replay_request (requested_at) WHERE status = 'PENDING';
