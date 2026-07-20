package org.sebastiandev.trip.gateway.api;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.OffsetDateTime;
import java.util.List;

@RegisterForReflection(targets = {
        BookingObservabilityModels.Summary.class,
        BookingObservabilityModels.Stage.class,
        BookingObservabilityModels.Communication.class,
        BookingObservabilityModels.Signals.class
})
public final class BookingObservabilityModels {
    private BookingObservabilityModels() {
    }

    public record Summary(
            boolean available,
            String unavailableReason,
            String bookingId,
            String primaryTraceId,
            List<String> traceIds,
            long totalDurationMs,
            List<Stage> stages,
            List<Communication> communications,
            Signals signals) {
    }

    public record Stage(
            String status,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            long durationMs,
            boolean active) {
    }

    public record Communication(
            String source,
            String target,
            String protocol,
            String destination,
            int count,
            long totalDurationMs,
            int errorCount) {
    }

    public record Signals(
            int retryCount,
            int duplicateCount,
            int dlqCount,
            int failedSpanCount,
            boolean compensationStarted,
            boolean refundRequested,
            String notificationStatus) {
    }
}
