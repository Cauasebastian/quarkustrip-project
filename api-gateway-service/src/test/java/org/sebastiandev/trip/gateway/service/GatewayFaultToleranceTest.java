package org.sebastiandev.trip.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.grpc.Status;
import java.lang.reflect.Method;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.junit.jupiter.api.Test;
import org.sebastiandev.trip.contracts.grpc.CreateBookingRequest;
import org.sebastiandev.trip.contracts.grpc.GetBookingRequest;
import org.sebastiandev.trip.gateway.api.FaultToleranceExceptionMapper;

class GatewayFaultToleranceTest {
    @Test
    void retriesOnlyIdempotentBookingQueries() throws Exception {
        Method query = BookingGatewayService.class.getMethod("get", GetBookingRequest.class);
        Method command = BookingGatewayService.class.getMethod("create", CreateBookingRequest.class);

        assertTrue(query.isAnnotationPresent(Retry.class));
        assertEquals(2, query.getAnnotation(Retry.class).maxRetries());
        assertFalse(command.isAnnotationPresent(Retry.class));
        assertEquals(3, BookingGatewayService.class.getAnnotation(Timeout.class).value());
    }

    @Test
    void classifiesOnlyAvailabilityFailuresAsRetryable() {
        GrpcFailureClassifier classifier = new GrpcFailureClassifier();
        var unavailable = Status.UNAVAILABLE.asRuntimeException();
        var invalid = Status.INVALID_ARGUMENT.asRuntimeException();

        assertInstanceOf(RetryableGrpcException.class, classifier.classify(unavailable));
        assertSame(invalid, classifier.classify(invalid));
    }

    @Test
    void mapsFaultToleranceFailuresToControlledServiceUnavailable() {
        var response = new FaultToleranceExceptionMapper().toResponse(
                new org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException());

        assertEquals(503, response.getStatus());
        assertEquals("2", response.getHeaderString("Retry-After"));
    }
}
