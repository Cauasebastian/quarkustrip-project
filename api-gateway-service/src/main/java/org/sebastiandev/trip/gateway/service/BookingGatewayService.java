package org.sebastiandev.trip.gateway.service;

import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.sebastiandev.trip.contracts.grpc.CancelBookingRequest;
import org.sebastiandev.trip.contracts.grpc.CancelBookingResponse;
import org.sebastiandev.trip.contracts.grpc.CreateBookingRequest;
import org.sebastiandev.trip.contracts.grpc.CreateBookingResponse;
import org.sebastiandev.trip.contracts.grpc.GetBookingRequest;
import org.sebastiandev.trip.contracts.grpc.GetBookingResponse;
import org.sebastiandev.trip.contracts.grpc.ListUserBookingsRequest;
import org.sebastiandev.trip.contracts.grpc.ListUserBookingsResponse;
import org.sebastiandev.trip.contracts.grpc.MutinyBookingCommandServiceGrpc;

@ApplicationScoped
@Timeout(value = 3, unit = ChronoUnit.SECONDS)
@CircuitBreaker(requestVolumeThreshold = 8, failureRatio = 0.5, delay = 5,
        delayUnit = ChronoUnit.SECONDS, successThreshold = 2,
        failOn = {RetryableGrpcException.class, TimeoutException.class})
public class BookingGatewayService {
    @Inject @GrpcClient("booking") MutinyBookingCommandServiceGrpc.MutinyBookingCommandServiceStub client;
    @Inject GrpcFailureClassifier failures;

    public Uni<CreateBookingResponse> create(CreateBookingRequest request) {
        return failures.classify(client.createBooking(request));
    }

    public Uni<CancelBookingResponse> cancel(CancelBookingRequest request) {
        return failures.classify(client.cancelBooking(request));
    }

    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS,
            jitter = 50, jitterDelayUnit = ChronoUnit.MILLIS, maxDuration = 5,
            durationUnit = ChronoUnit.SECONDS, retryOn = RetryableGrpcException.class)
    public Uni<GetBookingResponse> get(GetBookingRequest request) {
        return failures.classify(client.getBooking(request));
    }

    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS,
            jitter = 50, jitterDelayUnit = ChronoUnit.MILLIS, maxDuration = 5,
            durationUnit = ChronoUnit.SECONDS, retryOn = RetryableGrpcException.class)
    public Uni<ListUserBookingsResponse> list(ListUserBookingsRequest request) {
        return failures.classify(client.listUserBookings(request));
    }
}
