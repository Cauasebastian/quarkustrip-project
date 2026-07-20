package org.sebastiandev.trip.gateway.api;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class BookingApiModels {
    private BookingApiModels() {
    }

    public record CreateBooking(
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @NotBlank String paymentMethodRef,
            @NotEmpty List<@Valid Item> items) {
    }

    public record Item(
            @NotBlank String type,
            @NotBlank String resourceId,
            String seatNumber,
            LocalDate checkIn,
            LocalDate checkOut,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt) {
    }

    public record Cancel(String reason) {
    }

    @RegisterForReflection
    public record BookingCreated(String bookingId, String status, String location) {
    }

    public record BookingSummary(
            String id,
            String userId,
            String status,
            MoneyApiModel total,
            List<BookingItem> items,
            String failureCode,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }

    public record BookingItem(
            String id,
            String type,
            String resourceId,
            String status,
            String externalReservationId,
            MoneyApiModel price,
            String failureReason) {
    }

    public record BookingPage(
            List<BookingSummary> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    @RegisterForReflection
    public record BookingCancelled(String bookingId, String status) {
    }
}
