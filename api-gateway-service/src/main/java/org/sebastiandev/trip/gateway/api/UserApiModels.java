package org.sebastiandev.trip.gateway.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class UserApiModels {
    private UserApiModels() {
    }

    public record Profile(
            String id,
            String subject,
            String username,
            String email,
            String firstName,
            String lastName,
            String preferencesJson) {
    }

    public record UpdateProfile(
            @NotBlank @Email String email,
            String firstName,
            String lastName,
            String preferencesJson) {
    }

    public record ProfilePage(List<Profile> items, int page, int size, long totalElements) {
    }
}
