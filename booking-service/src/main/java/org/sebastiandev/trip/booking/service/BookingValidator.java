package org.sebastiandev.trip.booking.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Currency;
import java.util.UUID;
import org.sebastiandev.trip.contracts.grpc.CreateBookingRequest;

@ApplicationScoped
public class BookingValidator {
    public UUID validate(CreateBookingRequest request) {
        UUID userId = parseUuid(request.getUserId(), "userId");
        if (request.getIdempotencyKey().isBlank() || request.getIdempotencyKey().length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key is required and must have at most 128 characters");
        }
        try {
            Currency.getInstance(request.getCurrency());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("currency must be ISO-4217");
        }
        if (request.getPaymentMethodRef().isBlank()) {
            throw new IllegalArgumentException("paymentMethodRef is required");
        }
        if (request.getItemsCount() == 0) {
            throw new IllegalArgumentException("at least one booking item is required");
        }
        request.getItemsList().forEach(item -> {
            if (item.getItemCase() == org.sebastiandev.trip.contracts.grpc.BookingItemRequest.ItemCase.ITEM_NOT_SET) {
                throw new IllegalArgumentException("every booking item must have a type");
            }
        });
        return userId;
    }

    public UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }
}
