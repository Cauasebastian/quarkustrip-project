package org.sebastianDev.controller;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.sebastianDev.model.Hotel;
import org.sebastianDev.service.HotelService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Path("/hotels")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HotelController {

    @Inject
    HotelService hotelService;

    @GET
    public Uni<List<Hotel>> getAllHotels() {
        return hotelService.getAllHotels();
    }

    @GET
    @Path("/{id}")
    public Uni<Response> getHotelById(@PathParam("id") UUID id) {
        return hotelService.getHotelById(id)
                .onItem().ifNotNull().transform(hotel -> Response.ok(hotel).build())
                .onItem().ifNull().continueWith(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    public Uni<Response> createHotel(Hotel hotel) {
        return hotelService.createHotel(hotel)
                .onItem().transform(createdHotel -> Response.status(Response.Status.CREATED).entity(createdHotel).build());
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> deleteHotel(@PathParam("id") UUID id) {
        return hotelService.deleteHotel(id)
                .onItem().transform(deleted -> deleted
                        ? Response.noContent().build()
                        : Response.status(Response.Status.NOT_FOUND).build());
    }
}
