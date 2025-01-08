package org.sebastianDev.controller;


import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.sebastianDev.model.Room;
import org.sebastianDev.service.RoomService;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomController {

    @Inject
    RoomService roomService;

    @GET
    public Response getAllRooms() {
        List<Room> rooms = roomService.getAllRooms();
        return Response.ok(rooms).build();
    }

    @GET
    @Path("/{id}")
    public Response getRoomById(@PathParam("id") UUID id) {
        Room room = roomService.getRoomById(id);
        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(room).build();
    }

    @POST
    @Transactional
    public Response createRoom(Room room, @Context UriInfo uriInfo) {
        roomService.createRoom(room);
        URI uri = uriInfo.getAbsolutePathBuilder().path(room.id.toString()).build();
        return Response.created(uri).entity(room).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response updateRoom(@PathParam("id") UUID id, Room roomDetails) {
        Room updatedRoom = roomService.updateRoom(id, roomDetails);
        if (updatedRoom == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(updatedRoom).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteRoom(@PathParam("id") UUID id) {
        boolean deleted = roomService.deleteRoom(id);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }
}
