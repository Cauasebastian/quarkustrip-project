package org.sebastiandev.trip.gateway.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.sebastiandev.trip.contracts.grpc.BookingView;
import org.sebastiandev.trip.contracts.grpc.GetBookingRequest;
import org.sebastiandev.trip.gateway.api.BookingObservabilityModels;
import org.sebastiandev.trip.gateway.service.BookingGatewayService;

@ApplicationScoped
public class BookingObservabilityService {
    @Inject BookingGatewayService booking;
    @Inject JaegerGatewayService jaeger;
    @Inject JaegerTraceParser parser;

    public Uni<BookingObservabilityModels.Summary> get(String bookingId, UUID requesterId, boolean admin) {
        GetBookingRequest request = GetBookingRequest.newBuilder().setBookingId(bookingId)
                .setRequesterUserId(requesterId.toString()).setAdmin(admin).build();
        return booking.get(request).chain(response -> summarize(response.getBooking()));
    }

    private Uni<BookingObservabilityModels.Summary> summarize(BookingView value) {
        String traceId = value.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            return Uni.createFrom().item(parser.unavailable(value.getId(), null, "TRACING_DISABLED"));
        }
        return jaeger.trace(traceId).ifNoItem().after(Duration.ofSeconds(2)).fail()
                .onFailure(WebApplicationException.class).recoverWithItem(JsonNodeFactory.instance.objectNode())
                .chain(primary -> related(value).onFailure().recoverWithItem(JsonNodeFactory.instance.objectNode())
                        .map(related -> parser.parse(value.getId(), traceId, value.getStatus().name(),
                                primary, related)))
                .onFailure().recoverWithItem(() -> parser.unavailable(value.getId(), traceId,
                        "JAEGER_UNAVAILABLE"));
    }

    private Uni<JsonNode> related(BookingView value) {
        Instant created = Instant.ofEpochSecond(value.getCreatedAt().getSeconds(), value.getCreatedAt().getNanos());
        long startMicros = created.minusSeconds(60).toEpochMilli() * 1_000;
        long endMicros = Instant.now().plusSeconds(60).toEpochMilli() * 1_000;
        String tags = "{\"booking.id\":\"" + value.getId() + "\"}";
        return jaeger.traces("booking-service", tags, startMicros, endMicros, 20)
                .ifNoItem().after(Duration.ofSeconds(2)).fail();
    }
}
