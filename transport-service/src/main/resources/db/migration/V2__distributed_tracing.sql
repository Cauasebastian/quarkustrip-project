ALTER TABLE outbox_events ADD COLUMN trace_parent VARCHAR(55);
ALTER TABLE outbox_events ADD COLUMN trace_state VARCHAR(512);
ALTER TABLE transport_offers ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE transport_reservations ALTER COLUMN currency TYPE VARCHAR(3);
