CREATE TABLE user_profiles(id UUID PRIMARY KEY,subject VARCHAR(255) NOT NULL UNIQUE,email VARCHAR(320) NOT NULL,first_name VARCHAR(120),last_name VARCHAR(120),preferences JSONB NOT NULL DEFAULT '{}'::jsonb,created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL,version BIGINT NOT NULL DEFAULT 0);
CREATE TABLE outbox_events(id UUID PRIMARY KEY,topic VARCHAR(255) NOT NULL,aggregate_id UUID NOT NULL,payload JSONB NOT NULL,attempts INTEGER NOT NULL DEFAULT 0,created_at TIMESTAMPTZ NOT NULL,published_at TIMESTAMPTZ);
CREATE INDEX idx_user_outbox_pending ON outbox_events(created_at) WHERE published_at IS NULL;
CREATE TABLE inbox_events(event_id UUID PRIMARY KEY,type VARCHAR(255) NOT NULL,processed_at TIMESTAMPTZ NOT NULL);
