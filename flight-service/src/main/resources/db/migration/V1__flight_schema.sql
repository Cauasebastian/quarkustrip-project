CREATE TABLE flights (
    id UUID PRIMARY KEY,
    flight_number VARCHAR(32) NOT NULL UNIQUE,
    origin CHAR(3) NOT NULL,
    destination CHAR(3) NOT NULL,
    departure_time TIMESTAMPTZ NOT NULL,
    arrival_time TIMESTAMPTZ NOT NULL,
    seat_price_minor BIGINT NOT NULL CHECK (seat_price_minor >= 0),
    currency CHAR(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (arrival_time > departure_time)
);
CREATE TABLE flight_seats (
    id UUID PRIMARY KEY,
    flight_id UUID NOT NULL REFERENCES flights(id),
    seat_number VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    held_by_item_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (flight_id, seat_number)
);
CREATE TABLE flight_reservations (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    booking_item_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    flight_id UUID NOT NULL REFERENCES flights(id),
    seat_id UUID NOT NULL REFERENCES flight_seats(id),
    seat_number VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    hold_until TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_active_flight_seat ON flight_reservations(flight_id, seat_number)
    WHERE status IN ('HELD', 'CONFIRMED');
CREATE TABLE outbox_events (id UUID PRIMARY KEY, topic VARCHAR(255) NOT NULL, aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ);
CREATE INDEX idx_flight_outbox_pending ON outbox_events(created_at) WHERE published_at IS NULL;
CREATE TABLE inbox_events (event_id UUID PRIMARY KEY, type VARCHAR(255) NOT NULL, processed_at TIMESTAMPTZ NOT NULL);
