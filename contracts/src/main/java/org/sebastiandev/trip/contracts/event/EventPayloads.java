package org.sebastiandev.trip.contracts.event;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RegisterForReflection(targets = {
        EventPayloads.ReservationRequested.class,
        EventPayloads.ReservationOutcome.class,
        EventPayloads.ReservationAction.class,
        EventPayloads.PaymentRequested.class,
        EventPayloads.PaymentOutcome.class,
        EventPayloads.RefundRequested.class,
        EventPayloads.BookingTerminal.class,
        EventPayloads.UserProfileChanged.class,
        EventPayloads.NotificationOutcome.class
})
public final class EventPayloads {
    private EventPayloads() {}

    public record ReservationRequested(UUID bookingId, UUID bookingItemId, UUID userId,
                                       UUID resourceId, Map<String, String> attributes,
                                       String currency, OffsetDateTime holdUntil) {}

    public record ReservationOutcome(UUID bookingId, UUID bookingItemId, UUID reservationId,
                                     long amountMinor, String currency, String status, String reason) {}

    public record ReservationAction(UUID bookingId, UUID bookingItemId, UUID reservationId) {}

    public record PaymentRequested(UUID bookingId, UUID userId, long amountMinor,
                                   String currency, String paymentMethodRef) {}

    public record PaymentOutcome(UUID bookingId, UUID paymentId, String status, String reason) {}

    public record RefundRequested(UUID bookingId, UUID paymentId, String reason) {}

    public record BookingTerminal(UUID bookingId, UUID userId, String status,
                                  long amountMinor, String currency, String reason) {}

    public record UserProfileChanged(UUID userId, String subject, String email) {}

    public record NotificationOutcome(UUID notificationId, UUID bookingId, UUID userId,
                                      String channel, String status, String reason) {}
}
