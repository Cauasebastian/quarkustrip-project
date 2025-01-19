package org.sebastianDev.controller;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.sebastianDev.model.Booking;
import org.sebastianDev.service.BookingService;

import java.util.List;
import java.util.UUID;

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
    public Uni<Booking> getReservationById(UUID id) {
        return bookingService.getReservationById(id);
    }

    @POST
    public Uni<Booking> createReservation(Booking reservation) {
        return bookingService.createReservation(reservation);
    }
    @PUT
    @Path("/{id}")
    public Uni<Booking> updateReservation(@PathParam("id") UUID id, Booking reservationDetails) {
        return bookingService.updateReservation(id, reservationDetails);
    }
    @DELETE
    @Path("/{id}")
    public Uni<Boolean> deleteReservation(@PathParam("id") UUID id) {
        return bookingService.deleteReservation(id);
    }
}
