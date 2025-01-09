package org.sebastianDev.controller;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.sebastianDev.model.HotelReservation;
import org.sebastianDev.service.HotelReservationService;

import java.net.URI;
import java.util.UUID;
@Path("/reservations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HotelReservationController {

    @Inject
    HotelReservationService reservationService;

    @GET
    public Uni<Response> getAllReservations() {
        return reservationService.getAllReservations()
                .onItem().transform(reservations -> Response.ok(reservations).build());
    }

    @GET
    @Path("/{id}")
    public Uni<Response> getReservationById(@PathParam("id") UUID id) {
        return reservationService.getReservationById(id)
                .onItem().ifNotNull().transform(reservation -> Response.ok(reservation).build())
                .onItem().ifNull().continueWith(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    public Uni<Response> createReservation(HotelReservation reservation, @Context UriInfo uriInfo) {
        return reservationService.createReservation(reservation)
                .onItem().transform(createdReservation -> {
                    URI uri = uriInfo.getAbsolutePathBuilder().path(createdReservation.id.toString()).build();
                    return Response.created(uri).entity(createdReservation).build();
                });
    }

    @PUT
    @Path("/{id}")
    public Uni<Response> updateReservation(@PathParam("id") UUID id, HotelReservation reservationDetails) {
        return reservationService.updateReservation(id, reservationDetails)
                .onItem().ifNotNull().transform(updatedReservation -> Response.ok(updatedReservation).build())
                .onItem().ifNull().continueWith(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> deleteReservation(@PathParam("id") UUID id) {
        return reservationService.deleteReservation(id)
                .onItem().transform(deleted -> deleted
                        ? Response.noContent().build()
                        : Response.status(Response.Status.NOT_FOUND).build());
    }
}
