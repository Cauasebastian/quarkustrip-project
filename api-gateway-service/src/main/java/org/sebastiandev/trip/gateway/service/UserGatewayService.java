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
import org.sebastiandev.trip.contracts.grpc.GetUserProfileRequest;
import org.sebastiandev.trip.contracts.grpc.GetUserProfileResponse;
import org.sebastiandev.trip.contracts.grpc.MutinyUserProfileServiceGrpc;
import org.sebastiandev.trip.contracts.grpc.UpsertUserProfileRequest;
import org.sebastiandev.trip.contracts.grpc.UpsertUserProfileResponse;
import org.sebastiandev.trip.contracts.grpc.SearchUserProfilesRequest;
import org.sebastiandev.trip.contracts.grpc.SearchUserProfilesResponse;

@ApplicationScoped
@Timeout(value = 3, unit = ChronoUnit.SECONDS)
@CircuitBreaker(requestVolumeThreshold = 8, failureRatio = 0.5, delay = 5,
        delayUnit = ChronoUnit.SECONDS, successThreshold = 2,
        failOn = {RetryableGrpcException.class, TimeoutException.class})
public class UserGatewayService {
    @Inject @GrpcClient("user") MutinyUserProfileServiceGrpc.MutinyUserProfileServiceStub client;
    @Inject GrpcFailureClassifier failures;

    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS,
            jitter = 50, jitterDelayUnit = ChronoUnit.MILLIS, maxDuration = 5,
            durationUnit = ChronoUnit.SECONDS, retryOn = RetryableGrpcException.class)
    public Uni<GetUserProfileResponse> get(GetUserProfileRequest request) {
        return failures.classify(client.getProfile(request));
    }

    public Uni<UpsertUserProfileResponse> upsert(UpsertUserProfileRequest request) {
        return failures.classify(client.upsertProfile(request));
    }

    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 100, delayUnit = ChronoUnit.MILLIS,
            jitter = 50, jitterDelayUnit = ChronoUnit.MILLIS, maxDuration = 5,
            durationUnit = ChronoUnit.SECONDS, retryOn = RetryableGrpcException.class)
    public Uni<SearchUserProfilesResponse> search(SearchUserProfilesRequest request) {
        return failures.classify(client.searchProfiles(request));
    }
}
