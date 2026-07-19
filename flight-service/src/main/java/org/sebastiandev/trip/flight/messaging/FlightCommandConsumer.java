package org.sebastiandev.trip.flight.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.sebastiandev.trip.contracts.event.EventCodec;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.event.EventPayloads;
import org.sebastiandev.trip.contracts.event.EventSchemaValidator;
import org.sebastiandev.trip.flight.service.FlightApplicationService;

@ApplicationScoped
@Retry(maxRetries = 3, delay = 200)
public class FlightCommandConsumer {
    @Inject ObjectMapper mapper;
    @Inject FlightApplicationService service;

    @Incoming("reserve-flight")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> reserve(Message<String> message) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, message.getPayload());
        return service.reserve(event, EventCodec.payload(mapper, event, EventPayloads.ReservationRequested.class));
    }

    @Incoming("confirm-flight")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> confirm(Message<String> message) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, message.getPayload());
        return service.confirm(event, EventCodec.payload(mapper, event, EventPayloads.ReservationAction.class));
    }

    @Incoming("cancel-flight")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> cancel(Message<String> message) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, message.getPayload());
        return service.cancel(event, EventCodec.payload(mapper, event, EventPayloads.ReservationAction.class));
    }
}
