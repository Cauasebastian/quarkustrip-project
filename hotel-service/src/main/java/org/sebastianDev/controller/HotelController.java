package org.sebastianDev.controller;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.sebastianDev.model.Hotel;
import org.sebastianDev.service.HotelService;

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
    public Uni<Hotel> getHotelById(@PathParam("id") UUID id) {
        return hotelService.getHotelById(id);
    }

    @POST
    public Uni<Hotel> createHotel(Hotel hotel) {
        return hotelService.createHotel(hotel);
    }

    @PUT
    @Path("/{id}")
    public Uni<Hotel> updateHotel(@PathParam("id") UUID id, Hotel hotelDetails) {
        return hotelService.updateHotel(id, hotelDetails);
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> deleteHotel(@PathParam("id") UUID id) {
        return hotelService.deleteHotel(id)
                .onItem().transform(deleted -> deleted
                        ? Response.noContent().build()
                        : Response.status(Response.Status.NOT_FOUND).build()
                );
    }
}
