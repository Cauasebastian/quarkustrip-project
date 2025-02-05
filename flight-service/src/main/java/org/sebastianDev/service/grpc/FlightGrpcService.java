package org.sebastianDev.service.grpc;

import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.sebastianDev.*;
import org.sebastianDev.repository.FlightSeatRepository;
import org.sebastianDev.model.FlightSeat;
import org.sebastianDev.model.FlightReservation;
import org.sebastianDev.repository.FlightReservationRepository;

import java.util.UUID;

@GrpcService
public class FlightGrpcService implements FlightService {

    private static final Logger LOG = Logger.getLogger(FlightGrpcService.class);

    @Inject
    FlightSeatRepository flightSeatRepository;

    @Inject
    FlightReservationRepository flightReservationRepository;

    /**
     * Reserves a seat for the user.
     * @param request
     * @return Uni<ReserveSeatResponse>
     */
    @Override
    public Uni<ReserveSeatResponse> reserveSeat(ReserveSeatRequest request) {
        UUID flightId = UUID.fromString(request.getFlightId());
        String seatNumber = request.getSeatNumber();
        UUID userId = UUID.fromString(request.getUserId());

        LOG.infof("Received request: flightId=%s, seatNumber=%s, userId=%s", flightId, seatNumber, userId);

        return Panache.withTransaction(() ->
                flightSeatRepository.find("flight.id = ?1 and seatNumber = ?2", flightId, seatNumber)
                        .firstResult()
                        .onItem().ifNull().failWith(new RuntimeException("Seat not found"))
                        .onItem().transformToUni(seat -> {
                            if (seat.status == FlightSeat.SeatStatus.BOOKED) {
                                return Uni.createFrom().item(
                                        ReserveSeatResponse.newBuilder()
                                                .setSuccess(false)
                                                .setMessage("Seat is already booked")
                                                .build());
                            }

                            // Reserve the seat
                            seat.status = FlightSeat.SeatStatus.BOOKED;
                            seat.userId = userId;  // Assign userId to the seat

                            // Persist the updated seat information
                            return flightSeatRepository.persist(seat)
                                    .onItem().transformToUni(v -> {
                                        // Create the FlightReservation
                                        FlightReservation reservation = new FlightReservation();
                                        reservation.seat = seat;
                                        reservation.userId = userId;
                                        reservation.bookingId = UUID.randomUUID(); // Create a new booking ID
                                        reservation.status = FlightReservation.ReservationStatus.BOOKED;

                                        // Persist the reservation
                                        return flightReservationRepository.persist(reservation)
                                                .onItem().transform(res ->
                                                        ReserveSeatResponse.newBuilder()
                                                                .setSuccess(true)
                                                                .setMessage("Seat reserved and reservation created successfully")
                                                                .build());
                                    });
                        })
                        .onFailure().recoverWithItem(th -> ReserveSeatResponse.newBuilder()
                                .setSuccess(false)
                                .setMessage("Failed to reserve seat: " + th.getMessage())
                                .build())
        );
    }
}
