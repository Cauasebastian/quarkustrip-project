CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    total_amount_minor BIGINT NOT NULL DEFAULT 0 CHECK (total_amount_minor >= 0),
    payment_method_ref VARCHAR(255) NOT NULL,
    payment_id UUID,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    step_deadline TIMESTAMPTZ NOT NULL,
    saga_deadline TIMESTAMPTZ NOT NULL,
    failure_code VARCHAR(255),
    cancellation_requested BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE booking_items (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    type VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    resource_id UUID NOT NULL,
    request_data JSONB NOT NULL,
    reservation_id UUID,
    amount_minor BIGINT NOT NULL DEFAULT 0 CHECK (amount_minor >= 0),
    failure_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_booking_items_booking ON booking_items(booking_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_booking_outbox_pending ON outbox_events(created_at) WHERE published_at IS NULL;

CREATE TABLE inbox_events (
    event_id UUID PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
