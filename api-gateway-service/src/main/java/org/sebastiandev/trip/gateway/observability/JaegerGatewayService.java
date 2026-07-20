package org.sebastiandev.trip.gateway.observability;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
@Timeout(value = 1500, unit = ChronoUnit.MILLIS)
@CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 5,
        delayUnit = ChronoUnit.SECONDS, successThreshold = 1)
public class JaegerGatewayService {
    @Inject @RestClient JaegerQueryClient client;

    public Uni<JsonNode> trace(String traceId) {
        return client.trace(traceId);
    }

    public Uni<JsonNode> traces(String service, String tags, long startMicros, long endMicros, int limit) {
        return client.traces(service, tags, startMicros, endMicros, limit);
    }
}
