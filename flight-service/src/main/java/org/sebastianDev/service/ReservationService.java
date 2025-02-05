package org.sebastianDev.service;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.sebastianDev.dto.FlightReservationSummaryDTO;
import org.sebastianDev.dto.ReservationRequest;
import org.sebastianDev.model.FlightReservation;
import org.sebastianDev.model.FlightSeat;
import org.sebastianDev.repository.FlightReservationRepository;
import org.sebastianDev.repository.FlightSeatRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class ReservationService {

    @Inject
    FlightSeatRepository flightSeatRepository;

    @Inject
    FlightReservationRepository flightReservationRepository;

    public Uni<FlightReservation> createReservation(ReservationRequest request) {
        // Start the transaction
        return Panache.withTransaction(() ->
                flightSeatRepository.find("flight.id = ?1 and seatNumber = ?2", request.flightId, request.seatNumber)
                        .firstResult()
                        .onItem().ifNotNull().transformToUni(seat -> {
                            // Check if the seat is available
                            if (seat.status != FlightSeat.SeatStatus.AVAILABLE) {
                                throw new IllegalStateException("Seat not available");
                            }

                            // Mark the seat as booked
                            seat.status = FlightSeat.SeatStatus.BOOKED;

                            // Persist the seat update and create the reservation
                            return flightSeatRepository.persist(seat)
                                    .onItem().transformToUni(updatedSeat -> {
                                        FlightReservation reservation = new FlightReservation();
                                        reservation.seat = updatedSeat;
                                        reservation.userId = request.userId;
                                        reservation.bookingId = request.bookingId;

                                        // Persist the reservation
                                        return flightReservationRepository.persist(reservation);
                                    });
                        })
        );
    }

    // Method to get all reservations
    public Uni<List<FlightReservationSummaryDTO>> getAllReservations() {
        return flightReservationRepository.listAll()
                .map(reservations -> reservations.stream()
                        .map(this::convertToFlightReservationSummaryDTO)
                        .collect(Collectors.toList()));
    }

    // Method to get a reservation by ID
    public Uni<FlightReservationSummaryDTO> getReservationById(UUID id) {
        return flightReservationRepository.findById(id)
                .onItem().transform(reservation -> {
                    if (reservation != null) {
                        return convertToFlightReservationSummaryDTO(reservation);
                    } else {
                        return null; // Or throw an exception if necessary
                    }
                });
    }

    // Method to convert FlightReservation to DTO
    private FlightReservationSummaryDTO convertToFlightReservationSummaryDTO(FlightReservation reservation) {
        return new FlightReservationSummaryDTO(
                reservation.id,
                reservation.seat.seatNumber,
                reservation.bookingId,
                reservation.userId,
                reservation.status.name(),
                reservation.createdAt,
                reservation.updatedAt
        );
    }
}
