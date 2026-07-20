ALTER TABLE bookings ADD COLUMN payment_state VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUESTED';

UPDATE bookings
SET payment_state = CASE
    WHEN payment_id IS NOT NULL THEN 'SUCCEEDED'
    WHEN status = 'PAYMENT_PENDING' THEN 'PENDING'
    WHEN status IN ('CONFIRMING', 'CONFIRMED') THEN 'SUCCEEDED'
    ELSE 'NOT_REQUESTED'
END;

CREATE TABLE dlq_events (
    id VARCHAR(320) PRIMARY KEY,
    event_id UUID NOT NULL,
    original_topic VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_booking_dlq_event_id ON dlq_events(event_id);
