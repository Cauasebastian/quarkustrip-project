package org.sebastianDev.controller;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.sebastianDev.dto.FlightRequest;
import org.sebastianDev.service.FlightService;

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
    public Uni<Response> getFlightById(@PathParam("id") Long id) {
        return flightService.getFlightById(id)
                .onItem().transform(flight -> Response.ok(flight).build());
    }
}