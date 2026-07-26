package org.sebastiandev.trip.gateway.observability;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.sebastiandev.trip.gateway.api.BookingObservabilityModels;

@ApplicationScoped
public class JaegerTraceParser {
    public BookingObservabilityModels.Summary parse(String bookingId, String primaryTraceId,
                                                     String currentStatus, List<String> expectedServices,
                                                     JsonNode... responses) {
        List<SpanData> spans = readSpans(responses);
        if (spans.isEmpty()) {
            return unavailable(bookingId, primaryTraceId, "TRACE_NOT_FOUND", expectedServices);
        }

        spans.sort(Comparator.comparingLong(SpanData::startMicros));
        long started = spans.getFirst().startMicros();
        long finished = spans.stream().mapToLong(SpanData::endMicros).max().orElse(started);
        List<String> traceIds = spans.stream().map(SpanData::traceId).distinct().toList();
        List<String> observedServices = spans.stream().map(SpanData::service)
                .filter(service -> service != null && !service.isBlank() && !"unknown".equals(service))
                .distinct().sorted().toList();
        List<String> expected = expectedServices == null ? List.of() : List.copyOf(expectedServices);
        List<String> missing = expected.stream().filter(service -> !observedServices.contains(service)).toList();
        return new BookingObservabilityModels.Summary(true, null, bookingId, primaryTraceId, traceIds,
                missing.isEmpty(), expected, observedServices, missing,
                microsToMillis(finished - started), stages(spans, currentStatus, started, finished),
                communications(spans), signals(spans));
    }

    public BookingObservabilityModels.Summary unavailable(String bookingId, String traceId, String reason) {
        return unavailable(bookingId, traceId, reason, List.of());
    }

    public BookingObservabilityModels.Summary unavailable(String bookingId, String traceId, String reason,
                                                           List<String> expectedServices) {
        List<String> expected = expectedServices == null ? List.of() : List.copyOf(expectedServices);
        return new BookingObservabilityModels.Summary(false, reason, bookingId, traceId,
                traceId == null || traceId.isBlank() ? List.of() : List.of(traceId),
                false, expected, List.of(), expected, 0,
                List.of(), List.of(), new BookingObservabilityModels.Signals(0, 0, 0, 0,
                false, false, null));
    }

    private List<SpanData> readSpans(JsonNode... responses) {
        Map<String, SpanData> unique = new LinkedHashMap<>();
        for (JsonNode response : responses) {
            if (response == null || !response.path("data").isArray()) continue;
            for (JsonNode trace : response.path("data")) {
                String traceId = trace.path("traceID").asText();
                Map<String, String> processes = new HashMap<>();
                trace.path("processes").fields().forEachRemaining(entry ->
                        processes.put(entry.getKey(), entry.getValue().path("serviceName").asText("unknown")));
                for (JsonNode span : trace.path("spans")) {
                    String spanId = span.path("spanID").asText();
                    Map<String, String> tags = tags(span.path("tags"));
                    String parentId = null;
                    for (JsonNode reference : span.path("references")) {
                        if ("CHILD_OF".equals(reference.path("refType").asText())) {
                            parentId = reference.path("spanID").asText();
                            break;
                        }
                    }
                    SpanData value = new SpanData(traceId, spanId, parentId,
                            processes.getOrDefault(span.path("processID").asText(), "unknown"),
                            span.path("operationName").asText(), span.path("startTime").asLong(),
                            span.path("duration").asLong(), tags);
                    unique.putIfAbsent(traceId + ':' + spanId, value);
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<BookingObservabilityModels.Stage> stages(List<SpanData> spans, String currentStatus,
                                                           long traceStart, long traceEnd) {
        List<StateAt> transitions = new ArrayList<>();
        transitions.add(new StateAt("RESERVING", traceStart));
        for (SpanData span : spans) {
            if ("saga.transition".equals(span.operation())) {
                String state = span.tags().get("saga.state");
                if (state != null && !state.isBlank()) transitions.add(new StateAt(state, span.startMicros()));
            } else if ("saga.compensate".equals(span.operation())) {
                transitions.add(new StateAt("COMPENSATING", span.startMicros()));
            }
        }
        transitions.sort(Comparator.comparingLong(StateAt::atMicros));
        List<StateAt> normalized = new ArrayList<>();
        for (StateAt transition : transitions) {
            if (normalized.isEmpty() || !normalized.getLast().status().equals(transition.status())) {
                normalized.add(transition);
            }
        }
        Set<String> terminal = Set.of("CONFIRMED", "CANCELLED", "FAILED", "MANUAL_REVIEW");
        List<BookingObservabilityModels.Stage> result = new ArrayList<>();
        for (int index = 0; index < normalized.size(); index++) {
            StateAt stage = normalized.get(index);
            long end = index + 1 < normalized.size() ? normalized.get(index + 1).atMicros() : traceEnd;
            boolean active = index == normalized.size() - 1 && stage.status().equals(currentStatus)
                    && !terminal.contains(currentStatus);
            result.add(new BookingObservabilityModels.Stage(stage.status(), time(stage.atMicros()),
                    active ? null : time(end), microsToMillis(Math.max(0, end - stage.atMicros())), active));
        }
        return result;
    }

    private List<BookingObservabilityModels.Communication> communications(List<SpanData> spans) {
        Map<String, SpanData> byId = new HashMap<>();
        Map<String, SpanData> publishedByEvent = new HashMap<>();
        for (SpanData span : spans) {
            byId.put(span.traceId() + ':' + span.spanId(), span);
            if ("outbox.publish".equals(span.operation()) && span.tags().containsKey("event.id")) {
                publishedByEvent.put(span.tags().get("event.id"), span);
            }
        }

        Map<CommunicationKey, CommunicationAccumulator> result = new LinkedHashMap<>();
        for (SpanData span : spans) {
            String protocol = protocol(span);
            if (protocol == null) continue;
            String source;
            String target = span.service();
            String destination = destination(span);
            long duration = microsToMillis(span.durationMicros());
            if ("REST".equals(protocol)) {
                source = "browser";
            } else if ("GRPC".equals(protocol)) {
                SpanData parent = span.parentId() == null ? null : byId.get(span.traceId() + ':' + span.parentId());
                source = parent == null ? "grpc-client" : parent.service();
                if (source.equals(target)) continue;
            } else {
                String eventId = span.tags().get("event.id");
                SpanData publisher = eventId == null ? null : publishedByEvent.get(eventId);
                source = publisher == null ? "kafka" : publisher.service();
                if (publisher != null) {
                    duration = microsToMillis(Math.max(span.endMicros() - publisher.startMicros(),
                            span.durationMicros()));
                }
            }
            CommunicationKey key = new CommunicationKey(source, target, protocol, destination);
            result.computeIfAbsent(key, ignored -> new CommunicationAccumulator())
                    .add(duration, span.failed());
        }
        return result.entrySet().stream().map(entry -> new BookingObservabilityModels.Communication(
                entry.getKey().source(), entry.getKey().target(), entry.getKey().protocol(),
                entry.getKey().destination(), entry.getValue().count, entry.getValue().durationMs,
                entry.getValue().errors)).toList();
    }

    private BookingObservabilityModels.Signals signals(List<SpanData> spans) {
        Map<String, Integer> publishAttempts = new HashMap<>();
        Map<String, Integer> inboxAttempts = new HashMap<>();
        int duplicates = 0;
        int failures = 0;
        Set<String> dlq = new LinkedHashSet<>();
        boolean compensation = false;
        boolean refund = false;
        String notification = null;
        for (SpanData span : spans) {
            if (span.failed()) failures++;
            if ("outbox.publish".equals(span.operation())) {
                String event = span.tags().getOrDefault("event.id", span.spanId());
                publishAttempts.merge(event, integer(span.tags().get("outbox.attempt"), 1), Math::max);
            }
            if ("inbox.process".equals(span.operation())) {
                String event = span.service() + ':' + span.tags().getOrDefault("event.id", span.spanId());
                inboxAttempts.merge(event, 1, Integer::sum);
                if (Boolean.parseBoolean(span.tags().get("inbox.duplicate"))) duplicates++;
            }
            String destination = span.tags().get("messaging.destination.name");
            if ("messaging.dlq".equals(span.operation()) || destination != null && destination.endsWith(".dlq")) {
                dlq.add(span.tags().getOrDefault("event.id", span.spanId()) + ':' + destination);
            }
            compensation |= "saga.compensate".equals(span.operation());
            refund |= "payment.refund".equals(span.operation());
            if (span.tags().containsKey("notification.outcome")) {
                notification = span.tags().get("notification.outcome");
            }
        }
        int publishRetries = publishAttempts.values().stream().mapToInt(attempt -> Math.max(0, attempt - 1)).sum();
        int processRetries = inboxAttempts.values().stream().mapToInt(attempts -> Math.max(0, attempts - 1)).sum();
        return new BookingObservabilityModels.Signals(publishRetries + Math.max(0, processRetries - duplicates),
                duplicates, dlq.size(), failures, compensation, refund, notification);
    }

    private String protocol(SpanData span) {
        String operation = span.operation().toLowerCase(Locale.ROOT);
        if ("inbox.process".equals(span.operation())) return "KAFKA";
        if ("grpc".equalsIgnoreCase(span.tags().get("rpc.system"))
                && "server".equalsIgnoreCase(span.tags().get("span.kind"))) return "GRPC";
        if ((span.tags().containsKey("http.request.method") || span.tags().containsKey("http.method"))
                && "server".equalsIgnoreCase(span.tags().get("span.kind"))) return "REST";
        if (operation.matches("^(get|post|put|delete|patch) /.*")) return "REST";
        return null;
    }

    private String destination(SpanData span) {
        String destination = span.tags().get("messaging.destination.name");
        return destination == null || destination.isBlank() ? span.operation() : destination;
    }

    private Map<String, String> tags(JsonNode values) {
        Map<String, String> result = new HashMap<>();
        if (!values.isArray()) return result;
        for (JsonNode tag : values) result.put(tag.path("key").asText(), tag.path("value").asText());
        return result;
    }

    private int integer(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long microsToMillis(long micros) {
        return Math.max(0, micros / 1_000);
    }

    private OffsetDateTime time(long micros) {
        return Instant.ofEpochSecond(micros / 1_000_000, Math.floorMod(micros, 1_000_000) * 1_000)
                .atOffset(ZoneOffset.UTC);
    }

    private record SpanData(String traceId, String spanId, String parentId, String service, String operation,
                            long startMicros, long durationMicros, Map<String, String> tags) {
        long endMicros() {
            return startMicros + durationMicros;
        }

        boolean failed() {
            return Boolean.parseBoolean(tags.get("error"))
                    || "ERROR".equalsIgnoreCase(tags.get("otel.status_code"))
                    || "ERROR".equalsIgnoreCase(tags.get("status.code"));
        }
    }

    private record StateAt(String status, long atMicros) {
    }

    private record CommunicationKey(String source, String target, String protocol, String destination) {
    }

    private static final class CommunicationAccumulator {
        int count;
        long durationMs;
        int errors;

        void add(long duration, boolean failed) {
            count++;
            durationMs += duration;
            if (failed) errors++;
        }
    }
}
