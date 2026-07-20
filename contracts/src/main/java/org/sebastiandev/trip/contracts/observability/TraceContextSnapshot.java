package org.sebastiandev.trip.contracts.observability;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record TraceContextSnapshot(String traceParent, String traceState) {
    private static final Pattern W3C_TRACE_PARENT = Pattern.compile(
            "^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

    public static TraceContextSnapshot empty() {
        return new TraceContextSnapshot(null, null);
    }

    public boolean present() {
        return traceParent != null && !traceParent.isBlank();
    }

    public Optional<String> traceId() {
        if (!present()) return Optional.empty();
        Matcher matcher = W3C_TRACE_PARENT.matcher(traceParent);
        if (!matcher.matches() || matcher.group(1).chars().allMatch(value -> value == '0')) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }
}
