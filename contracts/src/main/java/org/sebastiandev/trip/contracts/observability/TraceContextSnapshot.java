package org.sebastiandev.trip.contracts.observability;

public record TraceContextSnapshot(String traceParent, String traceState) {
    public static TraceContextSnapshot empty() {
        return new TraceContextSnapshot(null, null);
    }

    public boolean present() {
        return traceParent != null && !traceParent.isBlank();
    }
}
