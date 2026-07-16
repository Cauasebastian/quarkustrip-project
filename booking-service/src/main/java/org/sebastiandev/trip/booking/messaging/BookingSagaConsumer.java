package org.sebastiandev.trip.booking.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.BiFunction;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.sebastiandev.trip.booking.domain.Booking;
import org.sebastiandev.trip.booking.domain.BookingItem;
import org.sebastiandev.trip.booking.domain.BookingItemStatus;
import org.sebastiandev.trip.booking.domain.BookingStatus;
import org.sebastiandev.trip.booking.repository.BookingRepository;
import org.sebastiandev.trip.booking.observability.SagaMetrics;
import org.sebastiandev.trip.booking.repository.InboxRepository;
import org.sebastiandev.trip.booking.service.BookingApplicationService;
import org.sebastiandev.trip.contracts.event.EventCodec;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.event.EventPayloads;
import org.sebastiandev.trip.contracts.event.EventSchemaValidator;
import org.sebastiandev.trip.contracts.event.TopicNames;

@ApplicationScoped
@Retry(maxRetries = 3, delay = 200)
public class BookingSagaConsumer {
    @Inject ObjectMapper mapper;
    @Inject BookingRepository bookings;
    @Inject InboxRepository inbox;
    @Inject BookingApplicationService service;
    @Inject OutboxService outbox;
    @Inject SagaMetrics metrics;

    @Incoming("flight-held") public Uni<Void> flightHeld(String json) { return reservationOutcome(json); }
    @Incoming("flight-failed") public Uni<Void> flightFailed(String json) { return reservationOutcome(json); }
    @Incoming("flight-confirmed") public Uni<Void> flightConfirmed(String json) { return reservationOutcome(json); }
    @Incoming("flight-cancelled") public Uni<Void> flightCancelled(String json) { return reservationOutcome(json); }
    @Incoming("hotel-held") public Uni<Void> hotelHeld(String json) { return reservationOutcome(json); }
    @Incoming("hotel-failed") public Uni<Void> hotelFailed(String json) { return reservationOutcome(json); }
    @Incoming("hotel-confirmed") public Uni<Void> hotelConfirmed(String json) { return reservationOutcome(json); }
    @Incoming("hotel-cancelled") public Uni<Void> hotelCancelled(String json) { return reservationOutcome(json); }
    @Incoming("transport-held") public Uni<Void> transportHeld(String json) { return reservationOutcome(json); }
    @Incoming("transport-failed") public Uni<Void> transportFailed(String json) { return reservationOutcome(json); }
    @Incoming("transport-confirmed") public Uni<Void> transportConfirmed(String json) { return reservationOutcome(json); }
    @Incoming("transport-cancelled") public Uni<Void> transportCancelled(String json) { return reservationOutcome(json); }

    private Uni<Void> reservationOutcome(String json) {
        EventEnvelope envelope = EventSchemaValidator.decodeValidated(mapper, json);
        EventPayloads.ReservationOutcome payload = EventCodec.payload(mapper, envelope,
                EventPayloads.ReservationOutcome.class);
        return process(envelope, payload.bookingId(), (booking, event) -> {
            BookingItem item = booking.items.stream().filter(candidate -> candidate.id.equals(payload.bookingItemId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("booking item not found"));
            item.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
            switch (payload.status()) {
                case "HELD" -> {
                    if (!booking.currency.equals(payload.currency())) {
                        item.status = BookingItemStatus.FAILED;
                        item.failureReason = "CURRENCY_MISMATCH";
                        return service.startCompensation(booking, "CURRENCY_MISMATCH", event.eventId());
                    }
                    item.status = BookingItemStatus.HELD;
                    item.reservationId = payload.reservationId();
                    item.amountMinor = payload.amountMinor();
                    if (booking.status == BookingStatus.COMPENSATING) {
                        return outbox.enqueue(switch (item.type) {
                                    case FLIGHT -> TopicNames.FLIGHT_CANCEL_REQUESTED;
                                    case HOTEL -> TopicNames.HOTEL_CANCEL_REQUESTED;
                                    case TRANSPORT -> TopicNames.TRANSPORT_CANCEL_REQUESTED;
                                }, booking.id, event.eventId(),
                                new EventPayloads.ReservationAction(booking.id, item.id, item.reservationId))
                                .replaceWithVoid();
                    }
                    if (booking.status != BookingStatus.RESERVING) return Uni.createFrom().voidItem();
                    if (booking.allItems(BookingItemStatus.HELD)) return service.startPayment(booking, event.eventId());
                }
                case "CONFIRMED" -> {
                    if (booking.status != BookingStatus.CONFIRMING) return Uni.createFrom().voidItem();
                    item.status = BookingItemStatus.CONFIRMED;
                    if (booking.allItems(BookingItemStatus.CONFIRMED)) {
                        booking.status = BookingStatus.CONFIRMED;
                        booking.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
                        metrics.terminal(booking);
                        return outbox.enqueue(TopicNames.BOOKING_CONFIRMED, booking.id, event.eventId(),
                                service.terminal(booking)).replaceWithVoid();
                    }
                }
                case "CANCELLED" -> {
                    item.status = BookingItemStatus.CANCELLED;
                    if (booking.status == BookingStatus.COMPENSATING) {
                        return service.finishCompensationIfPossible(booking, event.eventId());
                    }
                }
                default -> {
                    BookingItemStatus previous = item.status;
                    item.status = BookingItemStatus.FAILED;
                    item.failureReason = payload.reason();
                    if (booking.status == BookingStatus.COMPENSATING) {
                        if (previous == BookingItemStatus.PENDING) {
                            return service.finishCompensationIfPossible(booking, event.eventId());
                        }
                        return manualReview(booking, "COMPENSATION_FAILED", event.eventId());
                    }
                    return service.startCompensation(booking,
                            payload.reason() == null ? "RESERVATION_FAILED" : payload.reason(), event.eventId());
                }
            }
            return Uni.createFrom().voidItem();
        });
    }

    @Incoming("payment-succeeded")
    public Uni<Void> paymentSucceeded(String json) {
        EventEnvelope envelope = EventSchemaValidator.decodeValidated(mapper, json);
        EventPayloads.PaymentOutcome payload = EventCodec.payload(mapper, envelope, EventPayloads.PaymentOutcome.class);
        return process(envelope, payload.bookingId(), (booking, event) -> {
            if (booking.status != BookingStatus.PAYMENT_PENDING) return Uni.createFrom().voidItem();
            booking.paymentId = payload.paymentId();
            return service.startConfirmation(booking, event.eventId());
        });
    }

    @Incoming("payment-failed")
    public Uni<Void> paymentFailed(String json) {
        EventEnvelope envelope = EventSchemaValidator.decodeValidated(mapper, json);
        EventPayloads.PaymentOutcome payload = EventCodec.payload(mapper, envelope, EventPayloads.PaymentOutcome.class);
        return process(envelope, payload.bookingId(), (booking, event) -> {
            if (booking.status != BookingStatus.PAYMENT_PENDING) return Uni.createFrom().voidItem();
            return service.startCompensation(booking,
                    payload.reason() == null ? "PAYMENT_FAILED" : payload.reason(), event.eventId());
        });
    }

    @Incoming("payment-refunded")
    public Uni<Void> paymentRefunded(String json) {
        EventEnvelope envelope = EventSchemaValidator.decodeValidated(mapper, json);
        EventPayloads.PaymentOutcome payload = EventCodec.payload(mapper, envelope, EventPayloads.PaymentOutcome.class);
        return process(envelope, payload.bookingId(), (booking, event) -> {
            if (booking.status != BookingStatus.COMPENSATING) return Uni.createFrom().voidItem();
            booking.paymentId = null;
            return service.finishCompensationIfPossible(booking, event.eventId());
        });
    }

    @Incoming("payment-refund-failed")
    public Uni<Void> paymentRefundFailed(String json) {
        EventEnvelope envelope = EventSchemaValidator.decodeValidated(mapper, json);
        EventPayloads.PaymentOutcome payload = EventCodec.payload(mapper, envelope, EventPayloads.PaymentOutcome.class);
        return process(envelope, payload.bookingId(), (booking, event) ->
                manualReview(booking, "REFUND_FAILED", event.eventId()));
    }

    private Uni<Void> process(EventEnvelope envelope, UUID bookingId,
                              BiFunction<Booking, EventEnvelope, Uni<Void>> action) {
        return Panache.withTransaction(() -> inbox.findById(envelope.eventId()).chain(existing -> {
            if (existing != null) return Uni.createFrom().voidItem();
            return bookings.findById(bookingId)
                    .onItem().ifNull().failWith(() -> new IllegalArgumentException("booking not found"))
                    .chain(booking -> action.apply(booking, envelope))
                    .chain(() -> {
                        InboxEvent processed = new InboxEvent();
                        processed.eventId = envelope.eventId();
                        processed.type = envelope.type();
                        processed.processedAt = OffsetDateTime.now(ZoneOffset.UTC);
                        return inbox.persist(processed).replaceWithVoid();
                    });
        }));
    }

    private Uni<Void> manualReview(Booking booking, String reason, UUID causationId) {
        booking.status = BookingStatus.MANUAL_REVIEW;
        booking.failureCode = reason;
        booking.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        metrics.manualReview();
        return outbox.enqueue(TopicNames.BOOKING_MANUAL_REVIEW, booking.id, causationId,
                service.terminal(booking)).replaceWithVoid();
    }
}
