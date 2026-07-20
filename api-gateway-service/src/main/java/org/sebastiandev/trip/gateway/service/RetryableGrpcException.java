package org.sebastiandev.trip.gateway.service;

import io.grpc.StatusRuntimeException;

public class RetryableGrpcException extends StatusRuntimeException {
    public RetryableGrpcException(StatusRuntimeException cause) {
        super(cause.getStatus(), cause.getTrailers());
    }
}
