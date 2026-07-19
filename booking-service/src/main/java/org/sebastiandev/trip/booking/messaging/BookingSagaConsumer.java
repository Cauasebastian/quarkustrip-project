package org.sebastiandev.trip.booking.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.sebastiandev.trip.booking.service.BookingApplicationService;
import org.sebastiandev.trip.contracts.event.EventCodec;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.event.EventPayloads;
import org.sebastiandev.trip.contracts.event.EventSchemaValidator;

@ApplicationScoped
@Retry(maxRetries = 3, delay = 200)
public class BookingSagaConsumer {
    @Inject ObjectMapper mapper;
    @Inject BookingApplicationService service;

    @Incoming("flight-held") @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> flightHeld(Message<String> message) { return reservationOutcome(message); }
    @Incoming("flight-failed") @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> flightFailed(Message<String> message) { return reservationOutcome(message); }
    @Incoming("flight-confirmed") @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> flightConfirmed(Message<String> message) { return reservationOutcome(message); }
    @Incoming("flight-cancelled") @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> flightCancelled(Message<String> message) { return reservationOutcome(message); }
    @Incoming("hotel-held") @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> hotelHeld(Message<String> message) { return reservationOutcome(message); }
    @Incoming("hotel-failed") @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> hotelFailed(Message<String> message) { return reservationOutcome(message); }
    @Incoming("hotel-confirmed") @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> hotelConfirmed(Message<String> message) { return reservationOutcome(message); }
    @Incoming("hotel-cancelled") @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> hotelCancelled(Message<String> message) { return reservationOutcome(message); }
    @Incoming("transport-held") @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> transportHeld(Message<String> message) { return reservationOutcome(message); }
    @Incoming("transport-failed") @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> transportFailed(Message<String> message) { return reservationOutcome(message); }
    @Incoming("transport-confirmed") @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> transportConfirmed(Message<String> message) { return reservationOutcome(message); }
    @Incoming("transport-cancelled") @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> transportCancelled(Message<String> message) { return reservationOutcome(message); }

    @Incoming("payment-succeeded")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> paymentSucceeded(Message<String> message) {
        return paymentOutcome(message);
    }

    @Incoming("payment-failed")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> paymentFailed(Message<String> message) {
        return paymentOutcome(message);
    }

    @Incoming("payment-refunded")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> paymentRefunded(Message<String> message) {
        return paymentOutcome(message);
    }

    @Incoming("payment-refund-failed")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> paymentRefundFailed(Message<String> message) {
        return paymentOutcome(message);
    }

    private Uni<Void> reservationOutcome(Message<String> message) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, message.getPayload());
        return service.processReservationOutcome(event,
                EventCodec.payload(mapper, event, EventPayloads.ReservationOutcome.class));
    }

    private Uni<Void> paymentOutcome(Message<String> message) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, message.getPayload());
        return service.processPaymentOutcome(event,
                EventCodec.payload(mapper, event, EventPayloads.PaymentOutcome.class));
    }
}
