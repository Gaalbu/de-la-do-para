-- C79: W3C trace context of the request that wrote the event, so the trace continues through Kafka
-- into the consumer's effect. Optional: events written outside a traced request carry none.
ALTER TABLE event_outbox ADD COLUMN trace_parent VARCHAR(55);
