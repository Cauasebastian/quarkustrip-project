package org.sebastiandev.trip.gateway.api;

import io.grpc.StatusRuntimeException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GrpcExceptionMapper implements ExceptionMapper<StatusRuntimeException> {
    @Override
    public Response toResponse(StatusRuntimeException exception) {
        int code = switch (exception.getStatus().getCode()) {
            case INVALID_ARGUMENT -> 400;
            case UNAUTHENTICATED -> 401;
            case PERMISSION_DENIED -> 403;
            case NOT_FOUND -> 404;
            case ALREADY_EXISTS, ABORTED -> 409;
            case UNAVAILABLE, DEADLINE_EXCEEDED -> 503;
            default -> 500;
        };
        String message = exception.getStatus().getDescription() == null
                ? "request failed" : exception.getStatus().getDescription();
        return Response.status(code).entity(ApiError.of(exception.getStatus().getCode().name(), message)).build();
    }
}
