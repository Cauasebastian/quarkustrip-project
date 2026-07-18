package org.sebastiandev.trip.contracts.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TraceContextSupportTest {
    private static final InMemorySpanExporter EXPORTER = InMemorySpanExporter.create();
    private static SdkTracerProvider tracerProvider;
    private static OpenTelemetrySdk openTelemetry;

    @BeforeAll
    static void configureOpenTelemetry() {
        GlobalOpenTelemetry.resetForTest();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(EXPORTER))
                .build();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }

    @AfterAll
    static void closeOpenTelemetry() {
        tracerProvider.close();
        GlobalOpenTelemetry.resetForTest();
    }

    @BeforeEach
    void resetExporter() {
        EXPORTER.reset();
    }

    @Test
    void capturesAndRestoresW3cContext() {
        Span root = openTelemetry.getTracer("test").spanBuilder("root").startSpan();
        TraceContextSnapshot snapshot;
        try (Scope ignored = root.makeCurrent()) {
            snapshot = TraceContextSupport.captureCurrent();
        } finally {
            root.end();
        }

        assertTrue(snapshot.present());
        assertTrue(snapshot.traceParent().matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]"));
        assertEquals(root.getSpanContext().getTraceId(),
                Span.fromContext(TraceContextSupport.restore(snapshot)).getSpanContext().getTraceId());
    }

    @Test
    void rejectsInvalidPersistedContextWithoutThrowing() {
        Context restored = TraceContextSupport.restore(new TraceContextSnapshot("invalid", "vendor=value"));

        assertFalse(Span.fromContext(restored).getSpanContext().isValid());
    }

    @Test
    void recordsOutboxWaitAndPublishAsChildrenOfPersistedTrace() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Span root = openTelemetry.getTracer("test").spanBuilder("request").setSpanKind(SpanKind.SERVER).startSpan();
        TraceContextSnapshot snapshot;
        try (Scope ignored = root.makeCurrent()) {
            snapshot = TraceContextSupport.captureCurrent();
        }

        TraceContextSupport.OutboxPublishTrace publish = TraceContextSupport.beginOutboxPublish(eventId,
                bookingId, "trip.flight.reserve-requested.v1", 2,
                Instant.now().minus(50, ChronoUnit.MILLIS), snapshot);
        publish.finish(null);
        root.end();

        var spans = EXPORTER.getFinishedSpanItems();
        var waitSpan = spans.stream().filter(span -> span.getName().equals("outbox.wait")).findFirst().orElseThrow();
        var publishSpan = spans.stream().filter(span -> span.getName().equals("outbox.publish"))
                .findFirst().orElseThrow();
        assertEquals(root.getSpanContext().getTraceId(), waitSpan.getTraceId());
        assertEquals(root.getSpanContext().getTraceId(), publishSpan.getTraceId());
        assertEquals(bookingId.toString(), waitSpan.getAttributes().get(AttributeKey.stringKey("booking.id")));
        assertEquals(2L, publishSpan.getAttributes().get(AttributeKey.longKey("outbox.attempt")));
        assertNotNull(waitSpan.getAttributes().get(AttributeKey.longKey("outbox.wait_ms")));
    }
}
