package org.sebastiandev.trip.transport.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.sebastiandev.trip.contracts.event.EventCodec;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.event.EventPayloads;
import org.sebastiandev.trip.contracts.event.EventSchemaValidator;
import org.sebastiandev.trip.transport.service.TransportApplicationService;

@ApplicationScoped
@Retry(maxRetries = 3, delay = 200)
public class TransportCommandConsumer {
    @Inject ObjectMapper mapper;
    @Inject TransportApplicationService service;

    @Incoming("reserve-transport")
    public Uni<Void> reserve(String json) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, json);
        return service.reserve(event, EventCodec.payload(mapper, event, EventPayloads.ReservationRequested.class));
    }

    @Incoming("confirm-transport")
    public Uni<Void> confirm(String json) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, json);
        return service.confirm(event, EventCodec.payload(mapper, event, EventPayloads.ReservationAction.class));
    }

    @Incoming("cancel-transport")
    public Uni<Void> cancel(String json) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, json);
        return service.cancel(event, EventCodec.payload(mapper, event, EventPayloads.ReservationAction.class));
    }
}
