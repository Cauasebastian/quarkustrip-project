package org.sebastiandev.trip.payment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.sebastiandev.trip.contracts.event.EventCodec;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.event.EventPayloads;
import org.sebastiandev.trip.contracts.event.EventSchemaValidator;
import org.sebastiandev.trip.contracts.event.NonRetryableMessageException;
import org.sebastiandev.trip.payment.service.PaymentApplicationService;

@ApplicationScoped
@Retry(maxRetries = 2, delay = 200, abortOn = NonRetryableMessageException.class)
public class PaymentCommandConsumer {
    @Inject ObjectMapper mapper;
    @Inject PaymentApplicationService service;
    @Inject @ConfigProperty(name = "trip.payment.test-slow-delay", defaultValue = "65s") Duration slowDelay;

    @Incoming("process-payment")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> processPayment(Message<String> message) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, message.getPayload());
        EventPayloads.PaymentRequested request = EventCodec.payload(mapper, event,
                EventPayloads.PaymentRequested.class);
        Uni<Void> wait = "pm_test_slow".equals(request.paymentMethodRef())
                ? Uni.createFrom().voidItem().onItem().delayIt().by(slowDelay)
                : Uni.createFrom().voidItem();
        return wait.chain(() -> service.charge(event, request));
    }

    @Incoming("refund-payment")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public Uni<Void> refund(Message<String> message) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, message.getPayload());
        return service.refund(event, EventCodec.payload(mapper, event, EventPayloads.RefundRequested.class));
    }
}
