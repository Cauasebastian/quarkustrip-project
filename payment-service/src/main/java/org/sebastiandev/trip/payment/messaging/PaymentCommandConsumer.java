package org.sebastiandev.trip.payment.messaging;

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
import org.sebastiandev.trip.payment.service.PaymentApplicationService;

@ApplicationScoped
@Retry(maxRetries = 3, delay = 200)
public class PaymentCommandConsumer {
    @Inject ObjectMapper mapper;
    @Inject PaymentApplicationService service;

    @Incoming("process-payment")
    public Uni<Void> processPayment(String json) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, json);
        return service.charge(event, EventCodec.payload(mapper, event, EventPayloads.PaymentRequested.class));
    }

    @Incoming("refund-payment")
    public Uni<Void> refund(String json) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, json);
        return service.refund(event, EventCodec.payload(mapper, event, EventPayloads.RefundRequested.class));
    }
}
