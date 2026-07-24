ALTER TABLE bookings ADD COLUMN created_by_user_id UUID;
UPDATE bookings SET created_by_user_id = user_id WHERE created_by_user_id IS NULL;
ALTER TABLE bookings ALTER COLUMN created_by_user_id SET NOT NULL;
CREATE INDEX idx_bookings_created_by ON bookings(created_by_user_id, created_at DESC);

CREATE TABLE travel_packages (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    currency CHAR(3) NOT NULL,
    created_by_user_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE travel_package_items (
    id UUID PRIMARY KEY,
    package_id UUID NOT NULL REFERENCES travel_packages(id) ON DELETE CASCADE,
    type VARCHAR(16) NOT NULL,
    resource_id UUID NOT NULL,
    request_data JSONB NOT NULL,
    currency CHAR(3) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
    label VARCHAR(255) NOT NULL,
    detail VARCHAR(500)
);

CREATE INDEX idx_travel_packages_active ON travel_packages(created_at DESC) WHERE active = TRUE;
CREATE INDEX idx_travel_package_items_package ON travel_package_items(package_id);
