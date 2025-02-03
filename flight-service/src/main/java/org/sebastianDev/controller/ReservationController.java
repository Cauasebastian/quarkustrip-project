package org.sebastianDev.controller;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.sebastianDev.dto.ReservationRequest;
import org.sebastianDev.service.ReservationService;

@Path("/reservations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReservationController {

    @Inject
    ReservationService reservationService;

    @POST
    public Uni<Response> createReservation(ReservationRequest request) {
        return reservationService.createReservation(request)
                .onItem().transform(reservation -> Response.status(Response.Status.CREATED).entity(reservation).build())
                .onFailure().recoverWithItem(error -> Response.status(Response.Status.BAD_REQUEST).entity(error.getMessage()).build());
    }
}