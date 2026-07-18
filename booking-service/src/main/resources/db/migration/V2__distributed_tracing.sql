ALTER TABLE outbox_events ADD COLUMN trace_parent VARCHAR(55);
ALTER TABLE outbox_events ADD COLUMN trace_state VARCHAR(512);
ALTER TABLE bookings ADD COLUMN saga_trace_parent VARCHAR(55);
ALTER TABLE bookings ADD COLUMN saga_trace_state VARCHAR(512);
ALTER TABLE bookings ALTER COLUMN currency TYPE VARCHAR(3);
