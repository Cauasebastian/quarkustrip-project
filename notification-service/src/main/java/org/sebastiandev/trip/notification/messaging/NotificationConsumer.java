package org.sebastiandev.trip.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
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
    public Uni<Void> profileChanged(Message<String> message) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, message.getPayload());
        return service.updateContact(event,
                EventCodec.payload(mapper, event, EventPayloads.UserProfileChanged.class));
    }

    @Incoming("booking-confirmed")
    public Uni<Void> confirmed(Message<String> message) {
        return terminal(message);
    }

    @Incoming("booking-failed")
    public Uni<Void> failed(Message<String> message) {
        return terminal(message);
    }

    @Incoming("booking-cancelled")
    public Uni<Void> cancelled(Message<String> message) {
        return terminal(message);
    }

    @Incoming("booking-manual-review")
    public Uni<Void> manualReview(Message<String> message) {
        return terminal(message);
    }

    private Uni<Void> terminal(Message<String> message) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, message.getPayload());
        return service.notifyTerminal(event, EventCodec.payload(mapper, event, EventPayloads.BookingTerminal.class));
    }
}
