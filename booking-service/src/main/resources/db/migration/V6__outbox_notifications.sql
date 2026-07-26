ALTER TABLE outbox_events ADD COLUMN sequence_no BIGINT GENERATED ALWAYS AS IDENTITY;
CREATE INDEX idx_outbox_pending_sequence ON outbox_events(sequence_no) WHERE published_at IS NULL;

CREATE OR REPLACE FUNCTION notify_trip_outbox()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM pg_notify('trip_outbox', NEW.id::text);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_notify_trip_outbox
AFTER INSERT ON outbox_events
FOR EACH ROW
EXECUTE FUNCTION notify_trip_outbox();
