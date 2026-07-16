package org.sebastiandev.trip.contracts.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class EventSchemaValidator {
    private EventSchemaValidator() {}

    public static EventEnvelope decodeValidated(ObjectMapper mapper, String json) {
        try {
            JsonNode root = mapper.readTree(json);
            validate(mapper, root, "/schema/event-envelope-v1.schema.json");
            EventEnvelope envelope = mapper.treeToValue(root, EventEnvelope.class);
            String payloadSchema = payloadSchema(envelope.type());
            if (payloadSchema != null) validate(mapper, envelope.payload(), payloadSchema);
            return envelope;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid event JSON", exception);
        }
    }

    public static void validate(ObjectMapper mapper, JsonNode value, String schemaResource) {
        try (InputStream input = EventSchemaValidator.class.getResourceAsStream(schemaResource)) {
            if (input == null) throw new IllegalStateException("Schema not found: " + schemaResource);
            validateNode(value, mapper.readTree(input), "$", new HashSet<>());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read schema " + schemaResource, exception);
        }
    }

    private static void validateNode(JsonNode value, JsonNode schema, String path, Set<String> known) {
        JsonNode required = schema.get("required");
        if (required != null) required.forEach(name -> {
            if (!value.has(name.asText())) throw invalid(path + " is missing " + name.asText());
        });
        JsonNode properties = schema.get("properties");
        if (properties != null && value.isObject()) {
            properties.fields().forEachRemaining(entry -> {
                known.add(entry.getKey());
                if (value.has(entry.getKey())) validateScalar(value.get(entry.getKey()), entry.getValue(), path + "." + entry.getKey());
            });
            if (schema.path("additionalProperties").isBoolean() && !schema.path("additionalProperties").asBoolean()) {
                value.fieldNames().forEachRemaining(name -> {
                    if (!known.contains(name)) throw invalid(path + " contains unknown property " + name);
                });
            }
        }
    }

    private static void validateScalar(JsonNode value, JsonNode rule, String path) {
        if (rule.has("const") && !value.equals(rule.get("const"))) throw invalid(path + " has an invalid constant");
        if (rule.has("enum")) {
            boolean match = false;
            for (JsonNode allowed : rule.get("enum")) match |= allowed.equals(value);
            if (!match) throw invalid(path + " is outside the allowed values");
        }
        JsonNode type = rule.get("type");
        if (type != null && !matchesType(value, type)) throw invalid(path + " has an invalid type");
        if (value.isTextual()) {
            String text = value.asText();
            if (rule.has("minLength") && text.length() < rule.get("minLength").asInt()) throw invalid(path + " is too short");
            if (rule.has("pattern") && !Pattern.matches(rule.get("pattern").asText(), text)) throw invalid(path + " has an invalid format");
            if ("uuid".equals(rule.path("format").asText())) {
                try { UUID.fromString(text); } catch (RuntimeException exception) { throw invalid(path + " is not a UUID"); }
            }
            if ("date-time".equals(rule.path("format").asText())) {
                try { OffsetDateTime.parse(text); } catch (RuntimeException exception) { throw invalid(path + " is not an ISO date-time"); }
            }
        }
        if (value.isNumber() && rule.has("minimum") && value.asDouble() < rule.get("minimum").asDouble()) throw invalid(path + " is below minimum");
    }

    private static boolean matchesType(JsonNode value, JsonNode type) {
        if (type.isArray()) {
            for (JsonNode candidate : type) if (matchesType(value, candidate)) return true;
            return false;
        }
        return switch (type.asText()) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> true;
        };
    }

    private static String payloadSchema(String topic) {
        if (topic.endsWith("reserve-requested.v1")) return "/schema/reservation-requested-v1.schema.json";
        if (TopicNames.BOOKING_CONFIRMED.equals(topic) || TopicNames.BOOKING_FAILED.equals(topic)
                || TopicNames.BOOKING_CANCELLED.equals(topic) || TopicNames.BOOKING_MANUAL_REVIEW.equals(topic)) {
            return "/schema/booking-terminal-v1.schema.json";
        }
        if (topic.endsWith("held.v1") || topic.endsWith("confirmed.v1") || topic.endsWith("cancelled.v1")
                || (topic.endsWith("failed.v1") && !topic.startsWith("trip.payment") && !topic.startsWith("trip.booking"))) {
            return "/schema/reservation-outcome-v1.schema.json";
        }
        if (TopicNames.PAYMENT_PROCESS_REQUESTED.equals(topic)) return "/schema/payment-requested-v1.schema.json";
        return null;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
