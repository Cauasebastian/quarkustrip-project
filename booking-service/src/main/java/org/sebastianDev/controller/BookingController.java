package org.sebastianDev.controller;

import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.sebastianDev.model.Booking;
import org.sebastianDev.service.BookingService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Path("/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookingController {
    @Inject
    BookingService bookingService;

    @GET
    public Uni<String> getAllReservations() {
        return Uni.createFrom().item("Hello World");
    }


    @POST
    public Uni<Response> createReservation(Booking reservation) {
        return bookingService.createReservation(reservation)
                .onItem().transform(booking -> Response.ok(booking).build())
                .onFailure().recoverWithItem(e ->
                        Response.status(Response.Status.BAD_REQUEST)
                                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                                .build());
    }

}
