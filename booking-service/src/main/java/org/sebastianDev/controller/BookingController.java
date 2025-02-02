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
    public Uni<List<Booking>> getAllReservations() {
        return bookingService.getAllReservations();
    }

    @GET
    @Path("/{id}")
    public Uni<Response> getReservationById(@PathParam("id") UUID id) {
        return bookingService.getReservationById(id)
                .onItem().transform(booking -> Response.ok(booking).build())
                .onFailure().recoverWithItem(e ->
                        Response.status(Response.Status.NOT_FOUND)
                                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                                .build());
    }

    @PUT
    @Path("/{id}")
    public Uni<Response> updateReservation(@PathParam("id") UUID id, Booking reservation) {
        return bookingService.updateReservation(id, reservation)
                .onItem().transform(booking -> Response.ok(booking).build())
                .onFailure().recoverWithItem(e ->
                        Response.status(Response.Status.BAD_REQUEST)
                                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                                .build());
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> deleteReservation(@PathParam("id") UUID id) {
        return bookingService.deleteReservation(id)
                .onItem().transform(booking -> Response.noContent().build())
                .onFailure().recoverWithItem(e ->
                        Response.status(Response.Status.NOT_FOUND)
                                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                                .build());
    }

    @POST
    public Uni<Response> createReservation(Booking reservation) {
        return bookingService.createBooking(reservation)
                .onItem().transform(booking -> Response.ok(booking).build())
                .onFailure().recoverWithItem(e ->
                        Response.status(Response.Status.BAD_REQUEST)
                                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                                .build());
    }

}
