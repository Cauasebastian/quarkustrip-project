package org.sebastiandev.trip.contracts.event;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.OffsetDateTime;
import java.util.UUID;

@RegisterForReflection
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
