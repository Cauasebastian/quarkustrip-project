package org.sebastiandev.trip.gateway.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.grpc.Status;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.Validation;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiModelsTest {
    @Test
    void validatesStableAdminDto() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var invalid = new CatalogApiModels.CreateFlight(
                    "", "FO", "GRU", OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                    0, new MoneyApiModel("REAL", -1));
            assertFalse(validator.validate(invalid).isEmpty());
            var valid = new CatalogApiModels.CreateFlight(
                    "TP100", "FOR", "GRU", OffsetDateTime.now(), OffsetDateTime.now().plusHours(1),
                    12, new MoneyApiModel("BRL", 45_990));
            assertTrue(validator.validate(valid).isEmpty());
        }
    }

    @Test
    void validatesOperatorBookingDto() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var item = new BookingApiModels.Item("FLIGHT", "flight-id", "1A",
                    null, null, null, null);
            var valid = new OperatorApiModels.CreateBooking(
                    "3d568e2f-931d-41a8-9301-b475f2c24a10", "BRL", "pm_test_success", List.of(item));
            assertTrue(validator.validate(valid).isEmpty());
            var invalid = new OperatorApiModels.CreateBooking("", "REAL", "", List.of());
            assertFalse(validator.validate(invalid).isEmpty());
        }
    }

    @Test
    void serializesCamelCaseWithoutProtobufDetails() throws Exception {
        var flight = new CatalogApiModels.Flight("id", "TP100", "FOR", "GRU",
                OffsetDateTime.parse("2026-07-20T12:00:00Z"), OffsetDateTime.parse("2026-07-20T15:00:00Z"),
                new MoneyApiModel("BRL", 45_990), List.of("1A"));
        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(flight);
        assertTrue(json.contains("\"flightNumber\":\"TP100\""));
        assertTrue(json.contains("\"amountMinor\":45990"));
        assertFalse(json.contains("unknownFields"));
    }

    @Test
    void mapsGrpcErrorsToUniformEnvelope() {
        var response = new GrpcExceptionMapper().toResponse(
                Status.NOT_FOUND.withDescription("booking not found").asRuntimeException());
        assertEquals(404, response.getStatus());
        assertEquals(ApiError.of("NOT_FOUND", "booking not found"), response.getEntity());
        assertTrue(ApiError.class.isAnnotationPresent(RegisterForReflection.class));
        assertTrue(BookingApiModels.BookingCreated.class.isAnnotationPresent(RegisterForReflection.class));
        assertTrue(BookingApiModels.BookingCancelled.class.isAnnotationPresent(RegisterForReflection.class));
    }
}
