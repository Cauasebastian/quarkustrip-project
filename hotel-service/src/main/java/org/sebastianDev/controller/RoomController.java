package org.sebastianDev.controller;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.sebastianDev.model.Room;
import org.sebastianDev.service.RoomService;

import java.util.UUID;

@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomController {

    @Inject
    RoomService roomService;

    @GET
    public Uni<Response> getAllRooms() {
        return roomService.getAllRooms()
                .onItem().transform(rooms -> Response.ok(rooms).build());
    }

    @GET
    @Path("/{id}")
    public Uni<Response> getRoomById(@PathParam("id") UUID id) {
        return roomService.getRoomById(id)
                .onItem().ifNotNull().transform(room -> Response.ok(room).build())
                .onItem().ifNull().continueWith(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    public Uni<Room> createRoom(Room room) {
        return roomService.createRoom(room);
    }

    @PUT
    @Path("/{id}")
    public Uni<Response> updateRoom(@PathParam("id") UUID id, Room roomDetails) {
        return roomService.updateRoom(id, roomDetails)
                .onItem().ifNotNull().transform(updatedRoom -> Response.ok(updatedRoom).build())
                .onItem().ifNull().continueWith(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> deleteRoom(@PathParam("id") UUID id) {
        return roomService.deleteRoom(id)
                .onItem().transform(deleted -> deleted
                        ? Response.noContent().build()
                        : Response.status(Response.Status.NOT_FOUND).build());
    }
}