package org.sebastianDev.controller;

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
    public Response getAllHotels() {
        List<Hotel> hotels = hotelService.getAllHotels();
        return Response.ok(hotels).build();
    }

    @GET
    @Path("/{id}")
    public Response getHotelById(@PathParam("id") UUID id) {
        Hotel hotel = hotelService.getHotelById(id);
        if (hotel == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(hotel).build();
    }

    @POST
    @Transactional
    public Response createHotel(Hotel hotel, @Context UriInfo uriInfo) {
        hotelService.createHotel(hotel);
        URI uri = uriInfo.getAbsolutePathBuilder().path(hotel.id.toString()).build();
        return Response.created(uri).entity(hotel).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response updateHotel(@PathParam("id") UUID id, Hotel hotelDetails) {
        Hotel updatedHotel = hotelService.updateHotel(id, hotelDetails);
        if (updatedHotel == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updatedHotel).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteHotel(@PathParam("id") UUID id) {
        boolean deleted = hotelService.deleteHotel(id);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }
}
