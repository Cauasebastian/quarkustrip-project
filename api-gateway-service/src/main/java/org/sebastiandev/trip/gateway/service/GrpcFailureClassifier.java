package org.sebastiandev.trip.gateway.service;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GrpcFailureClassifier {
    public <T> Uni<T> classify(Uni<T> invocation) {
        return invocation.onFailure(StatusRuntimeException.class).transform(this::classify);
    }

    RuntimeException classify(Throwable failure) {
        StatusRuntimeException grpc = (StatusRuntimeException) failure;
        Status.Code code = grpc.getStatus().getCode();
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED
                ? new RetryableGrpcException(grpc) : grpc;
    }
}
