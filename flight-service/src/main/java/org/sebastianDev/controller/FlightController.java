package org.sebastianDev.controller;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.sebastianDev.dto.FlightDTO;
import org.sebastianDev.dto.FlightRequest;
import org.sebastianDev.service.FlightService;

import java.util.List;
import java.util.UUID;

@Path("/flights")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FlightController {

    @Inject
    FlightService flightService;

    @POST
    public Uni<Response> createFlight(FlightRequest request) {
        return flightService.createFlight(request)
                .onItem().transform(flight -> Response.status(Response.Status.CREATED).entity(flight).build());
    }
    @GET
    public Uni<Response> getAllFlights() {
        return flightService.getAllFlights()
                .onItem().transform(flights -> Response.ok(flights).build());
    }
    @GET
    @Path("/{id}")
    public Uni<Response> getFlightById(@PathParam("id") UUID id) {
        return flightService.getFlightById(id)
                .onItem().transform(flight -> Response.ok(flight).build());
    }
    @GET
    @Path("/{id}/seats")
    public Uni<Response> getFlightSeats(@PathParam("id") UUID id) {
        return flightService.getFlightSeatsSummary(id)
                .onItem().transform(seats -> Response.ok(seats).build())
                .onFailure().recoverWithItem(Response.status(Response.Status.NOT_FOUND).build());
    }
    // FlightController.java
    @GET
    @Path("/{id}/seats/grouped")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> getFlightSeatsGrouped(@PathParam("id") UUID id) {
        return flightService.getFlightSeatsSummaryGrouped(id)
                .onItem().transform(grouped -> Response.ok(grouped).build());
    }
}