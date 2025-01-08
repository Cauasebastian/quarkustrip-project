package org.sebastianDev.controller;


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
import java.util.List;
import java.util.UUID;

@Path("/reservations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HotelReservationController {

    @Inject
    HotelReservationService reservationService;

    @GET
    public Response getAllReservations() {
        List<HotelReservation> reservations = reservationService.getAllReservations();
        return Response.ok(reservations).build();
    }

    @GET
    @Path("/{id}")
    public Response getReservationById(@PathParam("id") UUID id) {
        HotelReservation reservation = reservationService.getReservationById(id);
        if (reservation == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(reservation).build();
    }

    @POST
    @Transactional
    public Response createReservation(HotelReservation reservation, @Context UriInfo uriInfo) {
        reservationService.createReservation(reservation);
        URI uri = uriInfo.getAbsolutePathBuilder().path(reservation.id.toString()).build();
        return Response.created(uri).entity(reservation).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response updateReservation(@PathParam("id") UUID id, HotelReservation reservationDetails) {
        HotelReservation updatedReservation = reservationService.updateReservation(id, reservationDetails);
        if (updatedReservation == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updatedReservation).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteReservation(@PathParam("id") UUID id) {
        boolean deleted = reservationService.deleteReservation(id);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }
}
