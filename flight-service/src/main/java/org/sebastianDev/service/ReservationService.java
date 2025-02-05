package org.sebastianDev.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import org.sebastianDev.dto.ReservationRequest;
import org.sebastianDev.model.FlightReservation;
import org.sebastianDev.model.FlightSeat;
import org.sebastianDev.repository.FlightReservationRepository;
import org.sebastianDev.repository.FlightSeatRepository;

@ApplicationScoped
public class ReservationService {

    @Inject
    FlightSeatRepository flightSeatRepository;

    @Inject
    FlightReservationRepository reservationRepository;

    public Uni<FlightReservation> createReservation(ReservationRequest request) {
        return flightSeatRepository.find("flight.id = ?1 and seatNumber = ?2", request.flightId, request.seatNumber)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult()
                .onItem().ifNotNull().transformToUni(seat -> {
                    if (seat.status != FlightSeat.SeatStatus.AVAILABLE) {
                        throw new IllegalStateException("Seat not available");
                    }
                    seat.status = FlightSeat.SeatStatus.BOOKED;
                    return flightSeatRepository.persist(seat)
                            .onItem().transformToUni(updatedSeat -> {
                                FlightReservation reservation = new FlightReservation();
                                reservation.seat = updatedSeat;
                                reservation.userId = request.userId;
                                reservation.bookingId = request.bookingId;  // Usando o bookingId do ReservationRequest
                                return reservationRepository.persist(reservation);
                            });
                });
    }
}
