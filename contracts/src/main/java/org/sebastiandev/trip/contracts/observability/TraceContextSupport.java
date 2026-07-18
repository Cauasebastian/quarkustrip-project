package org.sebastiandev.trip.contracts.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.smallrye.mutiny.Uni;
import java.util.HashMap;
import java.util.Map;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TraceContextSupport {
    public static final String TRACE_PARENT = "traceparent";
    public static final String TRACE_STATE = "tracestate";
    private static final TextMapSetter<Map<String, String>> SETTER = Map::put;
    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    };

    private TraceContextSupport() {
    }

    public static TraceContextSnapshot captureCurrent() {
        Map<String, String> carrier = new HashMap<>();
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .inject(Context.current(), carrier, SETTER);
        return new TraceContextSnapshot(carrier.get(TRACE_PARENT), carrier.get(TRACE_STATE));
    }

    public static Context restore(TraceContextSnapshot snapshot) {
        if (snapshot == null || !snapshot.present()) return Context.root();
        Map<String, String> carrier = new HashMap<>();
        carrier.put(TRACE_PARENT, snapshot.traceParent());
        if (snapshot.traceState() != null && !snapshot.traceState().isBlank()) {
            carrier.put(TRACE_STATE, snapshot.traceState());
        }
        Context extracted = GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.root(), carrier, GETTER);
        return Span.fromContext(extracted).getSpanContext().isValid() ? extracted : Context.root();
    }

    public static Span startSpan(String name, SpanKind kind, Context parent) {
        SpanBuilder builder = GlobalOpenTelemetry.getTracer("org.sebastiandev.trip")
                .spanBuilder(name).setSpanKind(kind);
        builder.setParent(parent == null ? Context.current() : parent);
        return builder.startSpan();
    }

    public static Span startSpan(String name, SpanKind kind, Context parent, Instant startedAt) {
        SpanBuilder builder = GlobalOpenTelemetry.getTracer("org.sebastiandev.trip")
                .spanBuilder(name).setSpanKind(kind).setStartTimestamp(startedAt);
        builder.setParent(parent == null ? Context.current() : parent);
        return builder.startSpan();
    }

    public static Span startLinkedSpan(String name, SpanKind kind, Context parent, Context linkedContext) {
        SpanBuilder builder = GlobalOpenTelemetry.getTracer("org.sebastiandev.trip")
                .spanBuilder(name).setSpanKind(kind).setParent(parent == null ? Context.current() : parent);
        if (linkedContext != null && Span.fromContext(linkedContext).getSpanContext().isValid()) {
            builder.addLink(Span.fromContext(linkedContext).getSpanContext());
        }
        return builder.startSpan();
    }

    public static OutboxPublishTrace beginOutboxPublish(UUID eventId, UUID bookingId, String destination,
                                                         int attempt, Instant createdAt,
                                                         TraceContextSnapshot snapshot) {
        Context parent = restore(snapshot);
        Instant dequeuedAt = Instant.now();
        Span wait = startSpan("outbox.wait", SpanKind.INTERNAL, parent, createdAt);
        setMessageAttributes(wait, eventId, bookingId, destination);
        wait.setAttribute("outbox.wait_ms", Math.max(0, dequeuedAt.toEpochMilli() - createdAt.toEpochMilli()));
        wait.end(dequeuedAt);

        Span publish = startSpan("outbox.publish", SpanKind.INTERNAL, parent);
        setMessageAttributes(publish, eventId, bookingId, destination);
        publish.setAttribute("outbox.attempt", attempt);
        return new OutboxPublishTrace(publish, parent.with(publish));
    }

    public static Span startInboxSpan(UUID eventId, UUID bookingId, String destination) {
        Span span = startSpan("inbox.process", SpanKind.INTERNAL, Context.current());
        setMessageAttributes(span, eventId, bookingId, destination);
        return span;
    }

    private static void setMessageAttributes(Span span, UUID eventId, UUID bookingId, String destination) {
        span.setAttribute("event.id", eventId.toString());
        span.setAttribute("messaging.destination.name", destination);
        if (bookingId != null) span.setAttribute("booking.id", bookingId.toString());
    }

    public static <T> T inContext(Span span, Supplier<T> action) {
        try (Scope ignored = span.makeCurrent()) {
            return action.get();
        } catch (RuntimeException exception) {
            fail(span, exception);
            throw exception;
        }
    }

    public static <T> T inContext(Context context, Supplier<T> action) {
        try (Scope ignored = context.makeCurrent()) {
            return action.get();
        }
    }

    public static <T> Uni<T> traceUni(String name, SpanKind kind, Consumer<Span> attributes,
                                      Function<Span, Uni<T>> action) {
        Span span = startSpan(name, kind, Context.current());
        attributes.accept(span);
        Uni<T> operation;
        try {
            operation = inContext(span, () -> action.apply(span));
        } catch (RuntimeException exception) {
            span.end();
            throw exception;
        }
        return operation.onItemOrFailure().invoke((ignored, failure) -> {
            if (failure != null) fail(span, failure);
            span.end();
        });
    }

    public static void fail(Span span, Throwable failure) {
        span.recordException(failure);
        span.setStatus(StatusCode.ERROR, failure.getMessage() == null ? failure.getClass().getSimpleName()
                : failure.getMessage());
    }

    public record OutboxPublishTrace(Span span, Context context) {
        public void finish(Throwable failure) {
            if (failure != null) TraceContextSupport.fail(span, failure);
            span.end();
        }
    }
}
