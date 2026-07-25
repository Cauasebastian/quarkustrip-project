package org.sebastiandev.trip.gateway.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class CatalogApiModels {
    private CatalogApiModels() {
    }

    public record Flight(
            String id,
            String flightNumber,
            String origin,
            String destination,
            OffsetDateTime departureTime,
            OffsetDateTime arrivalTime,
            MoneyApiModel seatPrice,
            List<String> availableSeats) {
    }

    public record Flights(List<Flight> items) {
    }

    public record CreateFlight(
            @NotBlank String flightNumber,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String origin,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String destination,
            @NotNull OffsetDateTime departureTime,
            @NotNull OffsetDateTime arrivalTime,
            @Positive int totalSeats,
            @NotNull @Valid MoneyApiModel seatPrice) {
    }

    public record Hotel(
            String id,
            String name,
            String address,
            String city,
            String country,
            int rating,
            boolean available) {
    }

    public record Hotels(
            List<Hotel> items,
            LocalDate checkIn,
            LocalDate checkOut,
            boolean defaultPeriod) {
    }

    public record CreateHotel(
            @NotBlank String name,
            @NotBlank String address,
            @NotBlank String city,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String country,
            @Min(0) @Max(5) int rating) {
    }

    public record Room(
            String id,
            String hotelId,
            String roomNumber,
            String roomType,
            MoneyApiModel nightlyPrice,
            boolean available) {
    }

    public record Rooms(List<Room> items) {
    }

    public record CreateRoom(
            @NotBlank String hotelId,
            @NotBlank String roomNumber,
            @NotBlank String roomType,
            @NotNull @Valid MoneyApiModel nightlyPrice) {
    }

    public record Transport(
            String id,
            String transportType,
            String providerName,
            String vehicleDetailsJson,
            MoneyApiModel price,
            boolean available) {
    }

    public record Transports(
            List<Transport> items,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            boolean defaultPeriod) {
    }

    public record CreateTransport(
            @NotBlank String transportType,
            @NotBlank String providerName,
            @NotBlank String vehicleDetailsJson,
            @NotNull @Valid MoneyApiModel price) {
    }
}
