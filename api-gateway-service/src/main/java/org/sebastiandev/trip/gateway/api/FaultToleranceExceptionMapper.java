package org.sebastiandev.trip.gateway.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceException;

@Provider
public class FaultToleranceExceptionMapper implements ExceptionMapper<FaultToleranceException> {
    @Override
    public Response toResponse(FaultToleranceException exception) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .header("Retry-After", "2")
                .entity(ApiError.of("DEPENDENCY_UNAVAILABLE", "dependent service is temporarily unavailable"))
                .build();
    }
}
