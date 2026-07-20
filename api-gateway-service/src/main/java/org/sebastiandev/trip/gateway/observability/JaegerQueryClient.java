package org.sebastiandev.trip.gateway.observability;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(configKey = "jaeger-query")
public interface JaegerQueryClient {
    @GET
    @Path("/traces/{traceId}")
    Uni<JsonNode> trace(@PathParam("traceId") String traceId);

    @GET
    @Path("/traces")
    Uni<JsonNode> traces(
            @QueryParam("service") String service,
            @QueryParam("tags") String tags,
            @QueryParam("start") long startMicros,
            @QueryParam("end") long endMicros,
            @QueryParam("limit") int limit);
}
