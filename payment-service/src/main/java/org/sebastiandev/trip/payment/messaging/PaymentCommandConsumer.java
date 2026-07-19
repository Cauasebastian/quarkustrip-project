package org.sebastiandev.trip.payment.messaging;

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
import org.sebastiandev.trip.payment.service.PaymentApplicationService;

@ApplicationScoped
@Retry(maxRetries = 3, delay = 200)
public class PaymentCommandConsumer {
    @Inject ObjectMapper mapper;
    @Inject PaymentApplicationService service;

    @Incoming("process-payment")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> processPayment(Message<String> message) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, message.getPayload());
        return service.charge(event, EventCodec.payload(mapper, event, EventPayloads.PaymentRequested.class));
    }

    @Incoming("refund-payment")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> refund(Message<String> message) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, message.getPayload());
        return service.refund(event, EventCodec.payload(mapper, event, EventPayloads.RefundRequested.class));
    }
}
