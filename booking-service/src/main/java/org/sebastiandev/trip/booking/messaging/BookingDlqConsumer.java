package org.sebastiandev.trip.booking.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Headers;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.sebastiandev.trip.booking.service.BookingApplicationService;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.event.EventSchemaValidator;
import org.sebastiandev.trip.contracts.event.NonRetryableMessageException;
import org.sebastiandev.trip.contracts.observability.TraceContextSupport;

@ApplicationScoped
public class BookingDlqConsumer {
    @Inject ObjectMapper mapper;
    @Inject BookingApplicationService service;

    @Incoming("saga-dlq")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @Retry(maxRetries = 2, delay = 200, abortOn = NonRetryableMessageException.class)
    public Uni<Void> process(Message<String> message) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, message.getPayload());
        IncomingKafkaRecordMetadata<?, ?> metadata = message.getMetadata(IncomingKafkaRecordMetadata.class)
                .orElseThrow(() -> new NonRetryableMessageException("Kafka metadata is required for DLQ"));
        String destination = metadata.getTopic();
        String original = header(metadata.getHeaders(), "dead-letter-topic",
                destination.endsWith(".dlq") ? destination.substring(0, destination.length() - 4) : destination);
        String failureClass = header(metadata.getHeaders(), "dead-letter-exception-class-name", "unknown");
        Span span = TraceContextSupport.startSpan("messaging.dlq", SpanKind.CONSUMER,
                io.opentelemetry.context.Context.current());
        span.setAttribute("booking.id", event.correlationId().toString());
        span.setAttribute("event.id", event.eventId().toString());
        span.setAttribute("messaging.destination.name", destination);
        span.setAttribute("messaging.original_destination", original);
        span.setAttribute("messaging.process.attempt", 3);
        span.setAttribute("error.type", sanitize(failureClass));
        return TraceContextSupport.inContext(span, () -> service.processDlq(event, original, sanitize(failureClass)))
                .onItemOrFailure().invoke((ignored, failure) -> {
                    if (failure != null) TraceContextSupport.fail(span, failure);
                    span.end();
                });
    }

    private String header(Headers headers, String name, String fallback) {
        var value = headers.lastHeader(name);
        return value == null ? fallback : new String(value.value(), StandardCharsets.UTF_8);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String normalized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }
}
