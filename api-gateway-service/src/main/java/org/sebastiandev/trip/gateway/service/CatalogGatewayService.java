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
import org.sebastiandev.trip.contracts.grpc.*;

@ApplicationScoped
@Timeout(value = 3, unit = ChronoUnit.SECONDS)
@CircuitBreaker(requestVolumeThreshold = 8, failureRatio = 0.5, delay = 5,
        delayUnit = ChronoUnit.SECONDS, successThreshold = 2,
        failOn = {RetryableGrpcException.class, TimeoutException.class})
public class CatalogGatewayService {
    @Inject @GrpcClient("flight") MutinyFlightQueryServiceGrpc.MutinyFlightQueryServiceStub flights;
    @Inject @GrpcClient("hotel") MutinyHotelQueryServiceGrpc.MutinyHotelQueryServiceStub hotels;
    @Inject @GrpcClient("transport") MutinyTransportQueryServiceGrpc.MutinyTransportQueryServiceStub transports;
    @Inject GrpcFailureClassifier failures;

    @Timeout(value = 2, unit = ChronoUnit.SECONDS) @Retry(maxRetries = 2, delay = 100,
            delayUnit = ChronoUnit.MILLIS, jitter = 50, jitterDelayUnit = ChronoUnit.MILLIS,
            maxDuration = 5, durationUnit = ChronoUnit.SECONDS, retryOn = RetryableGrpcException.class)
    public Uni<SearchFlightsResponse> searchFlights(SearchFlightsRequest request) {
        return failures.classify(flights.searchFlights(request));
    }

    public Uni<CreateFlightResponse> createFlight(CreateFlightRequest request) {
        return failures.classify(flights.createFlight(request));
    }

    @Timeout(value = 2, unit = ChronoUnit.SECONDS) @Retry(maxRetries = 2, delay = 100,
            delayUnit = ChronoUnit.MILLIS, jitter = 50, jitterDelayUnit = ChronoUnit.MILLIS,
            maxDuration = 5, durationUnit = ChronoUnit.SECONDS, retryOn = RetryableGrpcException.class)
    public Uni<SearchHotelsResponse> searchHotels(SearchHotelsRequest request) {
        return failures.classify(hotels.searchHotels(request));
    }

    @Timeout(value = 2, unit = ChronoUnit.SECONDS) @Retry(maxRetries = 2, delay = 100,
            delayUnit = ChronoUnit.MILLIS, jitter = 50, jitterDelayUnit = ChronoUnit.MILLIS,
            maxDuration = 5, durationUnit = ChronoUnit.SECONDS, retryOn = RetryableGrpcException.class)
    public Uni<ListRoomsResponse> listRooms(ListRoomsRequest request) {
        return failures.classify(hotels.listRooms(request));
    }

    public Uni<CreateHotelResponse> createHotel(CreateHotelRequest request) {
        return failures.classify(hotels.createHotel(request));
    }

    public Uni<CreateRoomResponse> createRoom(CreateRoomRequest request) {
        return failures.classify(hotels.createRoom(request));
    }

    @Timeout(value = 2, unit = ChronoUnit.SECONDS) @Retry(maxRetries = 2, delay = 100,
            delayUnit = ChronoUnit.MILLIS, jitter = 50, jitterDelayUnit = ChronoUnit.MILLIS,
            maxDuration = 5, durationUnit = ChronoUnit.SECONDS, retryOn = RetryableGrpcException.class)
    public Uni<SearchTransportsResponse> searchTransports(SearchTransportsRequest request) {
        return failures.classify(transports.searchTransports(request));
    }

    public Uni<CreateTransportResponse> createTransport(CreateTransportRequest request) {
        return failures.classify(transports.createTransport(request));
    }
}
