package org.sebastiandev.trip.contracts.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import java.util.UUID;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.sebastiandev.trip.contracts.grpc.HotelQueryServiceGrpc;
import org.sebastiandev.trip.contracts.grpc.ListUserBookingsResponse;

class EventContractTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void roundTripsVersionedEnvelope() {
        UUID bookingId = UUID.randomUUID();
        EventEnvelope envelope = EventCodec.envelope(mapper, TopicNames.BOOKING_CREATED,
                bookingId, null, "contracts-test", Map.of("bookingId", bookingId));
        EventEnvelope decoded = EventCodec.decode(mapper, EventCodec.encode(mapper, envelope));
        assertEquals(1, decoded.schemaVersion());
        assertEquals(bookingId, decoded.correlationId());
        assertNotNull(getClass().getResource("/schema/event-envelope-v1.schema.json"));
        assertNotNull(getClass().getResource("/schema/reservation-requested-v1.schema.json"));
        assertNotNull(getClass().getResource("/schema/reservation-outcome-v1.schema.json"));
        assertNotNull(getClass().getResource("/schema/payment-requested-v1.schema.json"));
        assertNotNull(getClass().getResource("/schema/booking-terminal-v1.schema.json"));
    }

    @Test
    void allTopicsAreVersionedAndUnique() {
        var topics = java.util.Arrays.stream(TopicNames.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .map(field -> {
                    try { return (String) field.get(null); }
                    catch (IllegalAccessException exception) { throw new AssertionError(exception); }
                }).toList();
        assertTrue(topics.stream().allMatch(topic -> topic.startsWith("trip.") && topic.endsWith(".v1")));
        assertEquals(topics.size(), topics.stream().distinct().count());
    }

    @Test
    void rejectsEnvelopeMissingRequiredMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> EventSchemaValidator.decodeValidated(mapper, "{\"schemaVersion\":1,\"payload\":{}}"));
    }

    @Test
    void exposesUiQueryContracts() {
        assertTrue(HotelQueryServiceGrpc.getServiceDescriptor().getMethods().stream()
                .anyMatch(method -> method.getFullMethodName().endsWith("/ListRooms")));
        assertNotNull(ListUserBookingsResponse.getDescriptor().findFieldByName("total_elements"));
        assertNotNull(ListUserBookingsResponse.getDescriptor().findFieldByName("page"));
        assertNotNull(ListUserBookingsResponse.getDescriptor().findFieldByName("size"));
    }
}
