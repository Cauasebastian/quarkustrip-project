package org.sebastiandev.trip.booking.domain;

public enum BookingStatus {
    RESERVING,
    PAYMENT_PENDING,
    CONFIRMING,
    CONFIRMED,
    COMPENSATING,
    CANCELLED,
    FAILED,
    MANUAL_REVIEW
}
