package org.sebastiandev.trip.booking.domain;

public enum PaymentState {
    NOT_REQUESTED,
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUND_PENDING,
    REFUNDED,
    REFUND_FAILED;

    public boolean settledForCompensation() {
        return this == NOT_REQUESTED || this == FAILED || this == REFUNDED;
    }
}
