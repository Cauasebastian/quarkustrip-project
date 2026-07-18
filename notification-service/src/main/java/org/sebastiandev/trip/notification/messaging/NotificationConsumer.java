package org.sebastiandev.trip.notification.messaging;

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
import org.sebastiandev.trip.notification.service.NotificationApplicationService;

@ApplicationScoped
@Retry(maxRetries = 3, delay = 200)
public class NotificationConsumer {
    @Inject ObjectMapper mapper;
    @Inject NotificationApplicationService service;

    @Incoming("user-profile-changed")
    public Uni<Void> profileChanged(String json) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, json);
        return service.updateContact(event,
                EventCodec.payload(mapper, event, EventPayloads.UserProfileChanged.class));
    }

    @Incoming("booking-confirmed")
    public Uni<Void> confirmed(String json) {
        return terminal(json);
    }

    @Incoming("booking-failed")
    public Uni<Void> failed(String json) {
        return terminal(json);
    }

    @Incoming("booking-cancelled")
    public Uni<Void> cancelled(String json) {
        return terminal(json);
    }

    @Incoming("booking-manual-review")
    public Uni<Void> manualReview(String json) {
        return terminal(json);
    }

    private Uni<Void> terminal(String json) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, json);
        return service.notifyTerminal(event, EventCodec.payload(mapper, event, EventPayloads.BookingTerminal.class));
    }
}
