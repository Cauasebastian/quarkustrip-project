ALTER TABLE outbox_events ADD COLUMN trace_parent VARCHAR(55);
ALTER TABLE outbox_events ADD COLUMN trace_state VARCHAR(512);
