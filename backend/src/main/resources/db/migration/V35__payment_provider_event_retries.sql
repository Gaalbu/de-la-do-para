-- C64: a provider lookup that does not (yet) show the payment is retried with backoff instead of discarding the
-- notification; exhausted or contradictory notifications stay visible to the operator as REVIEW.
ALTER TABLE payment_provider_event
    ADD COLUMN attempts        INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_error      VARCHAR(64),
    ADD CONSTRAINT payment_provider_event_attempts_nonnegative CHECK (attempts >= 0);

ALTER TABLE payment_provider_event
    DROP CONSTRAINT payment_provider_event_status_valid,
    ADD CONSTRAINT payment_provider_event_status_valid
        CHECK (status IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'REVIEW'));
