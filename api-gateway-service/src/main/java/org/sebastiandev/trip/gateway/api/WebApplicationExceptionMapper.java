package org.sebastiandev.trip.gateway.api;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
    @Override
    public Response toResponse(WebApplicationException exception) {
        int status = exception.getResponse().getStatus();
        String reason = exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getResponse().getStatusInfo().getReasonPhrase() : exception.getMessage();
        return Response.status(status).entity(ApiError.of("HTTP_" + status, reason)).build();
    }
}
