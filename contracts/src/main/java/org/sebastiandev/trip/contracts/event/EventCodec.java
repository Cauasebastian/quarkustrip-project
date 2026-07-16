package org.sebastiandev.trip.contracts.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public final class EventCodec {
    private EventCodec() {}

    public static EventEnvelope envelope(ObjectMapper mapper, String type, UUID correlationId,
                                         UUID causationId, String producer, Object payload) {
        return new EventEnvelope(UUID.randomUUID(), type, 1, OffsetDateTime.now(ZoneOffset.UTC),
                correlationId, causationId, producer, mapper.valueToTree(payload));
    }

    public static String encode(ObjectMapper mapper, EventEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize event " + envelope.type(), e);
        }
    }

    public static EventEnvelope decode(ObjectMapper mapper, String json) {
        try {
            return mapper.readValue(json, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid event envelope", e);
        }
    }

    public static <T> T payload(ObjectMapper mapper, EventEnvelope envelope, Class<T> type) {
        return mapper.convertValue(envelope.payload(), type);
    }
}
