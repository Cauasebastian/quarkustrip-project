package org.sebastiandev.trip.contracts.event;

public final class TopicNames {
    private TopicNames() {}

    public static String dlq(String topic) {
        if (!topic.endsWith(".v1")) throw new IllegalArgumentException("topic must be versioned");
        return topic + ".dlq";
    }

    public static final String BOOKING_CREATED = "trip.booking.created.v1";
    public static final String BOOKING_CONFIRMED = "trip.booking.confirmed.v1";
    public static final String BOOKING_FAILED = "trip.booking.failed.v1";
    public static final String BOOKING_CANCELLED = "trip.booking.cancelled.v1";
    public static final String BOOKING_MANUAL_REVIEW = "trip.booking.manual-review.v1";

    public static final String FLIGHT_RESERVE_REQUESTED = "trip.flight.reserve-requested.v1";
    public static final String FLIGHT_HELD = "trip.flight.held.v1";
    public static final String FLIGHT_FAILED = "trip.flight.failed.v1";
    public static final String FLIGHT_CONFIRM_REQUESTED = "trip.flight.confirm-requested.v1";
    public static final String FLIGHT_CONFIRMED = "trip.flight.confirmed.v1";
    public static final String FLIGHT_CANCEL_REQUESTED = "trip.flight.cancel-requested.v1";
    public static final String FLIGHT_CANCELLED = "trip.flight.cancelled.v1";

    public static final String HOTEL_RESERVE_REQUESTED = "trip.hotel.reserve-requested.v1";
    public static final String HOTEL_HELD = "trip.hotel.held.v1";
    public static final String HOTEL_FAILED = "trip.hotel.failed.v1";
    public static final String HOTEL_CONFIRM_REQUESTED = "trip.hotel.confirm-requested.v1";
    public static final String HOTEL_CONFIRMED = "trip.hotel.confirmed.v1";
    public static final String HOTEL_CANCEL_REQUESTED = "trip.hotel.cancel-requested.v1";
    public static final String HOTEL_CANCELLED = "trip.hotel.cancelled.v1";

    public static final String TRANSPORT_RESERVE_REQUESTED = "trip.transport.reserve-requested.v1";
    public static final String TRANSPORT_HELD = "trip.transport.held.v1";
    public static final String TRANSPORT_FAILED = "trip.transport.failed.v1";
    public static final String TRANSPORT_CONFIRM_REQUESTED = "trip.transport.confirm-requested.v1";
    public static final String TRANSPORT_CONFIRMED = "trip.transport.confirmed.v1";
    public static final String TRANSPORT_CANCEL_REQUESTED = "trip.transport.cancel-requested.v1";
    public static final String TRANSPORT_CANCELLED = "trip.transport.cancelled.v1";

    public static final String PAYMENT_PROCESS_REQUESTED = "trip.payment.process-requested.v1";
    public static final String PAYMENT_SUCCEEDED = "trip.payment.succeeded.v1";
    public static final String PAYMENT_FAILED = "trip.payment.failed.v1";
    public static final String PAYMENT_REFUND_REQUESTED = "trip.payment.refund-requested.v1";
    public static final String PAYMENT_REFUNDED = "trip.payment.refunded.v1";
    public static final String PAYMENT_REFUND_FAILED = "trip.payment.refund-failed.v1";

    public static final String USER_PROFILE_CHANGED = "trip.user.profile-changed.v1";
    public static final String NOTIFICATION_SENT = "trip.notification.sent.v1";
    public static final String NOTIFICATION_FAILED = "trip.notification.failed.v1";
}
