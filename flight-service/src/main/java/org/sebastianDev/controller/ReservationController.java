package org.sebastianDev.controller;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.sebastianDev.dto.ReservationRequest;
import org.sebastianDev.service.ReservationService;
import org.jboss.logging.Logger;

@Path("/reservations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReservationController {

    @Inject
    ReservationService reservationService;

    private static final Logger LOGGER = Logger.getLogger(ReservationController.class);

    // Create a flight reservation
    @POST
    public Uni<Response> createReservation(ReservationRequest request) {
        // Validate input
        if (request.flightId == null || request.seatNumber == null || request.userId == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST)
                    .entity("Flight ID, seat number, and user ID must be provided.").build());
        }

        // Log reservation attempt
        LOGGER.infof("Attempting to create reservation for flight ID: %s, seat number: %s, user ID: %s",
                request.flightId, request.seatNumber, request.userId);

        return reservationService.createReservation(request)
                .onItem().transform(reservation -> {
                    // Success response with reservation details
                    LOGGER.infof("Reservation created successfully with booking ID: %s", reservation.bookingId);
                    return Response.status(Response.Status.CREATED)
                            .entity(reservation)
                            .build();
                })
                .onFailure().recoverWithItem(e -> {
                    // Handle errors during the reservation process
                    LOGGER.errorf(e, "Error creating reservation for flight ID: %s, seat number: %s", request.flightId, request.seatNumber);
                    if (e instanceof IllegalStateException) {
                        return Response.status(Response.Status.CONFLICT)
                                .entity("The seat is not available for booking.")
                                .build();
                    }
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                            .entity("An error occurred while processing your reservation.")
                            .build();
                });
    }
}
