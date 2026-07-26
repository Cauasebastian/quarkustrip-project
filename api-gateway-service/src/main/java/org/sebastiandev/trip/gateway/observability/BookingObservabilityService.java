package org.sebastiandev.trip.gateway.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.sebastiandev.trip.contracts.grpc.BookingItemType;
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
        List<String> expectedServices = expectedServices(value);
        if (traceId == null || traceId.isBlank()) {
            return Uni.createFrom().item(parser.unavailable(value.getId(), null, "TRACING_DISABLED",
                    expectedServices));
        }
        return jaeger.trace(traceId).ifNoItem().after(Duration.ofSeconds(2)).fail()
                .onFailure(WebApplicationException.class).recoverWithItem(JsonNodeFactory.instance.objectNode())
                .chain(primary -> related(value).onFailure().recoverWithItem(JsonNodeFactory.instance.objectNode())
                        .map(related -> parser.parse(value.getId(), traceId, value.getStatus().name(),
                                expectedServices, primary, related)))
                .onFailure().recoverWithItem(() -> parser.unavailable(value.getId(), traceId,
                        "JAEGER_UNAVAILABLE", expectedServices));
    }

    private List<String> expectedServices(BookingView value) {
        Set<String> services = new LinkedHashSet<>();
        services.add("api-gateway-service");
        services.add("booking-service");
        value.getItemsList().forEach(item -> {
            if (item.getType() == BookingItemType.FLIGHT) services.add("flight-service");
            if (item.getType() == BookingItemType.HOTEL) services.add("hotel-service");
            if (item.getType() == BookingItemType.TRANSPORT) services.add("transport-service");
        });
        if (value.hasTotal() && value.getTotal().getAmountMinor() > 0) {
            services.add("payment-service");
        }
        switch (value.getStatus()) {
            case CONFIRMED, CANCELLED, FAILED, MANUAL_REVIEW -> services.add("notification-service");
            default -> {
            }
        }
        return new ArrayList<>(services);
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
