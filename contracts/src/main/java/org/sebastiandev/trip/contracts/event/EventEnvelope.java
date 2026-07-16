package org.sebastiandev.trip.contracts.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String type,
        int schemaVersion,
        OffsetDateTime occurredAt,
        UUID correlationId,
        UUID causationId,
        String producer,
        JsonNode payload) {
}
