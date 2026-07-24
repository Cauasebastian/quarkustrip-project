package org.sebastiandev.trip.gateway.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public final class OperatorApiModels {
    private OperatorApiModels() {
    }

    public record CreateBooking(
            @NotBlank String userId,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @NotBlank String paymentMethodRef,
            @NotEmpty List<BookingApiModels.@Valid Item> items) {
    }

    public record CreatePackage(
            @NotBlank String name,
            String description,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @NotEmpty List<@Valid PackageItemInput> items) {
    }

    public record PackageItemInput(
            @NotNull @Valid BookingApiModels.Item item,
            @NotNull @Valid MoneyApiModel displayPrice,
            @NotBlank String label,
            String detail) {
    }

    public record PackageItem(
            String id,
            BookingApiModels.Item item,
            String type,
            String resourceId,
            MoneyApiModel displayPrice,
            String label,
            String detail) {
    }

    public record TravelPackage(
            String id,
            String name,
            String description,
            String currency,
            List<PackageItem> items,
            OffsetDateTime createdAt) {
    }

    public record PackagePage(
            List<TravelPackage> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
