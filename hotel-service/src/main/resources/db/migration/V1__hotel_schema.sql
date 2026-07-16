CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE TABLE hotels (id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL, address VARCHAR(255) NOT NULL,
 city VARCHAR(120) NOT NULL, country CHAR(2) NOT NULL, rating INTEGER NOT NULL CHECK(rating BETWEEN 0 AND 5), version BIGINT NOT NULL DEFAULT 0);
CREATE TABLE rooms (id UUID PRIMARY KEY, hotel_id UUID NOT NULL REFERENCES hotels(id), room_number VARCHAR(32) NOT NULL,
 room_type VARCHAR(64) NOT NULL, nightly_price_minor BIGINT NOT NULL CHECK(nightly_price_minor>=0), currency CHAR(3) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 0, UNIQUE(hotel_id,room_number));
CREATE TABLE hotel_reservations (id UUID PRIMARY KEY, booking_id UUID NOT NULL, booking_item_id UUID NOT NULL UNIQUE,
 user_id UUID NOT NULL, room_id UUID NOT NULL REFERENCES rooms(id), check_in DATE NOT NULL, check_out DATE NOT NULL,
 status VARCHAR(16) NOT NULL, amount_minor BIGINT NOT NULL, currency CHAR(3) NOT NULL, hold_until TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0, CHECK(check_out>check_in));
ALTER TABLE hotel_reservations ADD CONSTRAINT no_overlapping_active_room_reservations
 EXCLUDE USING gist (room_id WITH =, daterange(check_in, check_out, '[)') WITH &&) WHERE (status IN ('HELD','CONFIRMED'));
CREATE TABLE outbox_events (id UUID PRIMARY KEY,topic VARCHAR(255) NOT NULL,aggregate_id UUID NOT NULL,payload JSONB NOT NULL,attempts INTEGER NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL,published_at TIMESTAMPTZ);
CREATE INDEX idx_hotel_outbox_pending ON outbox_events(created_at) WHERE published_at IS NULL;
CREATE TABLE inbox_events(event_id UUID PRIMARY KEY,type VARCHAR(255) NOT NULL,processed_at TIMESTAMPTZ NOT NULL);
