package org.sebastiandev.trip.contracts.event;

public class NonRetryableMessageException extends IllegalArgumentException {
    public NonRetryableMessageException(String message) {
        super(message);
    }

    public NonRetryableMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
