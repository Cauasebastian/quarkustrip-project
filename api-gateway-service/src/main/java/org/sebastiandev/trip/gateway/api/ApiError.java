package org.sebastiandev.trip.gateway.api;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;

@RegisterForReflection
public record ApiError(String error, String message, Map<String, String> fieldErrors) {
    public static ApiError of(String error, String message) {
        return new ApiError(error, message, Map.of());
    }
}
