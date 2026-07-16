package org.sebastiandev.trip.flight.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Function;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.sebastiandev.trip.contracts.event.EventCodec;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.event.EventPayloads;
import org.sebastiandev.trip.contracts.event.EventSchemaValidator;
import org.sebastiandev.trip.contracts.event.TopicNames;
import org.sebastiandev.trip.flight.domain.FlightReservation;
import org.sebastiandev.trip.flight.domain.FlightSeat;
import org.sebastiandev.trip.flight.repository.FlightReservationRepository;
import org.sebastiandev.trip.flight.repository.FlightSeatRepository;
import org.sebastiandev.trip.flight.repository.InboxRepository;

@ApplicationScoped
@Retry(maxRetries = 3, delay = 200)
public class FlightCommandConsumer {
    @Inject ObjectMapper mapper;
    @Inject FlightSeatRepository seats;
    @Inject FlightReservationRepository reservations;
    @Inject InboxRepository inbox;
    @Inject OutboxService outbox;

    @Incoming("reserve-flight")
    public Uni<Void> reserve(String json) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, json);
        EventPayloads.ReservationRequested request = EventCodec.payload(mapper, event,
                EventPayloads.ReservationRequested.class);
        return process(event, ignored -> reservations.find("bookingItemId", request.bookingItemId()).firstResult()
                .chain(existing -> existing == null ? hold(request, event) : publish(existing, event.eventId())));
    }

    private Uni<Void> hold(EventPayloads.ReservationRequested request, EventEnvelope event) {
        String seatNumber = request.attributes().get("seatNumber");
        if (seatNumber == null || seatNumber.isBlank()) return failure(request, event, "SEAT_REQUIRED");
        return seats.find("flight.id = ?1 and seatNumber = ?2", request.resourceId(), seatNumber)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult()
                .chain(seat -> {
                    if (seat == null) return failure(request, event, "SEAT_NOT_FOUND");
                    if (seat.status != FlightSeat.Status.AVAILABLE
                            && !request.bookingItemId().equals(seat.heldByItemId)) {
                        return failure(request, event, "SEAT_UNAVAILABLE");
                    }
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    seat.status = FlightSeat.Status.HELD;
                    seat.heldByItemId = request.bookingItemId();
                    FlightReservation reservation = new FlightReservation();
                    reservation.id = UUID.randomUUID(); reservation.bookingId = request.bookingId();
                    reservation.bookingItemId = request.bookingItemId(); reservation.userId = request.userId();
                    reservation.flightId = request.resourceId(); reservation.seatId = seat.id;
                    reservation.seatNumber = seat.seatNumber; reservation.status = FlightReservation.Status.HELD;
                    reservation.amountMinor = seat.flight.seatPriceMinor; reservation.currency = seat.flight.currency;
                    reservation.holdUntil = request.holdUntil(); reservation.createdAt = now; reservation.updatedAt = now;
                    return reservations.persist(reservation).chain(() -> publish(reservation, event.eventId()));
                });
    }

    @Incoming("confirm-flight")
    public Uni<Void> confirm(String json) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, json);
        EventPayloads.ReservationAction request = EventCodec.payload(mapper, event, EventPayloads.ReservationAction.class);
        return process(event, ignored -> reservations.findById(request.reservationId()).chain(reservation -> {
            if (reservation == null || !reservation.bookingItemId.equals(request.bookingItemId())) {
                return outcome(request.bookingId(), request.bookingItemId(), request.reservationId(), 0, "XXX",
                        "FAILED", "RESERVATION_NOT_FOUND", TopicNames.FLIGHT_FAILED, event.eventId());
            }
            if (reservation.status == FlightReservation.Status.CONFIRMED) return publish(reservation, event.eventId());
            if (reservation.status != FlightReservation.Status.HELD
                    || reservation.holdUntil.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
                return outcome(reservation, "FAILED", "HOLD_EXPIRED", TopicNames.FLIGHT_FAILED, event.eventId());
            }
            return seats.findById(reservation.seatId, LockModeType.PESSIMISTIC_WRITE).chain(seat -> {
                if (seat == null || !reservation.bookingItemId.equals(seat.heldByItemId)) {
                    return outcome(reservation, "FAILED", "SEAT_HOLD_LOST", TopicNames.FLIGHT_FAILED, event.eventId());
                }
                reservation.status = FlightReservation.Status.CONFIRMED;
                reservation.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
                seat.status = FlightSeat.Status.CONFIRMED;
                return publish(reservation, event.eventId());
            });
        }));
    }

    @Incoming("cancel-flight")
    public Uni<Void> cancel(String json) {
        EventEnvelope event = EventSchemaValidator.decodeValidated(mapper, json);
        EventPayloads.ReservationAction request = EventCodec.payload(mapper, event, EventPayloads.ReservationAction.class);
        return process(event, ignored -> reservations.findById(request.reservationId()).chain(reservation -> {
            if (reservation == null) {
                return outcome(request.bookingId(), request.bookingItemId(), request.reservationId(), 0, "XXX",
                        "CANCELLED", null, TopicNames.FLIGHT_CANCELLED, event.eventId());
            }
            if (reservation.status == FlightReservation.Status.CANCELLED) return publish(reservation, event.eventId());
            return seats.findById(reservation.seatId, LockModeType.PESSIMISTIC_WRITE).chain(seat -> {
                if (seat != null && reservation.bookingItemId.equals(seat.heldByItemId)) {
                    seat.status = FlightSeat.Status.AVAILABLE; seat.heldByItemId = null;
                }
                reservation.status = FlightReservation.Status.CANCELLED;
                reservation.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
                return publish(reservation, event.eventId());
            });
        }));
    }

    private Uni<Void> process(EventEnvelope event, Function<EventEnvelope, Uni<Void>> action) {
        return Panache.withTransaction(() -> inbox.findById(event.eventId()).chain(existing -> {
            if (existing != null) return Uni.createFrom().voidItem();
            return action.apply(event).chain(() -> {
                InboxEvent processed = new InboxEvent(); processed.eventId = event.eventId();
                processed.type = event.type(); processed.processedAt = OffsetDateTime.now(ZoneOffset.UTC);
                return inbox.persist(processed).replaceWithVoid();
            });
        }));
    }

    private Uni<Void> failure(EventPayloads.ReservationRequested request, EventEnvelope event, String reason) {
        return outcome(request.bookingId(), request.bookingItemId(), null, 0, request.currency(), "FAILED", reason,
                TopicNames.FLIGHT_FAILED, event.eventId());
    }

    private Uni<Void> publish(FlightReservation reservation, UUID causationId) {
        String status = reservation.status.name();
        String topic = switch (reservation.status) {
            case HELD -> TopicNames.FLIGHT_HELD;
            case CONFIRMED -> TopicNames.FLIGHT_CONFIRMED;
            case CANCELLED, EXPIRED -> TopicNames.FLIGHT_CANCELLED;
        };
        return outcome(reservation, status, null, topic, causationId);
    }

    private Uni<Void> outcome(FlightReservation reservation, String status, String reason, String topic, UUID cause) {
        return outcome(reservation.bookingId, reservation.bookingItemId, reservation.id, reservation.amountMinor,
                reservation.currency, status, reason, topic, cause);
    }

    private Uni<Void> outcome(UUID bookingId, UUID itemId, UUID reservationId, long amount, String currency,
                              String status, String reason, String topic, UUID cause) {
        return outbox.enqueue(topic, bookingId, cause, new EventPayloads.ReservationOutcome(bookingId, itemId,
                reservationId, amount, currency, status, reason)).replaceWithVoid();
    }
}
