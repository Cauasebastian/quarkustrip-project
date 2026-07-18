package org.sebastiandev.trip.hotel.service;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.event.EventPayloads;
import org.sebastiandev.trip.contracts.event.TopicNames;
import org.sebastiandev.trip.contracts.observability.TraceContextSupport;
import org.sebastiandev.trip.hotel.domain.HotelReservation;
import org.sebastiandev.trip.hotel.messaging.InboxEvent;
import org.sebastiandev.trip.hotel.messaging.OutboxService;
import org.sebastiandev.trip.hotel.repository.HotelReservationRepository;
import org.sebastiandev.trip.hotel.repository.InboxRepository;
import org.sebastiandev.trip.hotel.repository.RoomRepository;

@ApplicationScoped
public class HotelApplicationService {
    @Inject RoomRepository rooms;
    @Inject HotelReservationRepository reservations;
    @Inject InboxRepository inbox;
    @Inject OutboxService outbox;

    public Uni<Void> reserve(EventEnvelope event, EventPayloads.ReservationRequested request) {
        return process(event, () -> traceReservation("reservation.hold", "hold", event,
                () -> reservations.find("bookingItemId", request.bookingItemId()).firstResult()
                        .chain(existing -> existing == null ? hold(request, event)
                                : publish(existing, event.eventId()))));
    }

    public Uni<Void> confirm(EventEnvelope event, EventPayloads.ReservationAction request) {
        return process(event, () -> traceReservation("reservation.confirm", "confirm", event,
                () -> reservations.findById(request.reservationId()).chain(reservation -> {
            if (reservation == null || !reservation.bookingItemId.equals(request.bookingItemId())) {
                return raw(request.bookingId(), request.bookingItemId(), request.reservationId(), 0, "XXX", "FAILED",
                        "RESERVATION_NOT_FOUND", TopicNames.HOTEL_FAILED, event.eventId());
            }
            if (reservation.status == HotelReservation.Status.CONFIRMED) {
                return publish(reservation, event.eventId());
            }
            if (reservation.status != HotelReservation.Status.HELD
                    || reservation.holdUntil.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
                return outcome(reservation, "FAILED", "HOLD_EXPIRED", TopicNames.HOTEL_FAILED, event.eventId());
            }
            reservation.status = HotelReservation.Status.CONFIRMED;
            reservation.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
            return publish(reservation, event.eventId());
        })));
    }

    public Uni<Void> cancel(EventEnvelope event, EventPayloads.ReservationAction request) {
        return process(event, () -> traceReservation("reservation.cancel", "cancel", event,
                () -> reservations.findById(request.reservationId()).chain(reservation -> {
            if (reservation == null) {
                return raw(request.bookingId(), request.bookingItemId(), request.reservationId(), 0, "XXX",
                        "CANCELLED", null, TopicNames.HOTEL_CANCELLED, event.eventId());
            }
            reservation.status = HotelReservation.Status.CANCELLED;
            reservation.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
            return publish(reservation, event.eventId());
        })));
    }

    private Uni<Void> hold(EventPayloads.ReservationRequested request, EventEnvelope event) {
        LocalDate checkIn;
        LocalDate checkOut;
        try {
            checkIn = LocalDate.parse(request.attributes().get("checkIn"));
            checkOut = LocalDate.parse(request.attributes().get("checkOut"));
        } catch (RuntimeException exception) {
            return failure(request, event, "INVALID_DATES");
        }
        if (!checkOut.isAfter(checkIn)) return failure(request, event, "INVALID_DATE_RANGE");
        LocalDate start = checkIn;
        LocalDate end = checkOut;
        return rooms.findById(request.resourceId(), LockModeType.PESSIMISTIC_WRITE).chain(room -> {
            if (room == null || !room.active) return failure(request, event, "ROOM_NOT_FOUND");
            return reservations.count("roomId = ?1 and status in (?2, ?3) and checkIn < ?4 and checkOut > ?5",
                            request.resourceId(), HotelReservation.Status.HELD, HotelReservation.Status.CONFIRMED,
                            end, start)
                    .chain(conflicts -> {
                        if (conflicts > 0) return failure(request, event, "ROOM_UNAVAILABLE");
                        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                        HotelReservation reservation = new HotelReservation();
                        reservation.id = UUID.randomUUID();
                        reservation.bookingId = request.bookingId();
                        reservation.bookingItemId = request.bookingItemId();
                        reservation.userId = request.userId();
                        reservation.roomId = request.resourceId();
                        reservation.checkIn = start;
                        reservation.checkOut = end;
                        reservation.status = HotelReservation.Status.HELD;
                        reservation.amountMinor = Math.multiplyExact(ChronoUnit.DAYS.between(start, end),
                                room.nightlyPriceMinor);
                        reservation.currency = room.currency;
                        reservation.holdUntil = request.holdUntil();
                        reservation.createdAt = now;
                        reservation.updatedAt = now;
                        return reservations.persist(reservation).chain(() -> publish(reservation, event.eventId()));
                    });
        });
    }

    private Uni<Void> process(EventEnvelope event, Supplier<Uni<Void>> action) {
        Span span = TraceContextSupport.startInboxSpan(event.eventId(), event.correlationId(), event.type());
        return TraceContextSupport.inContext(span, () -> Panache.withTransaction(() ->
                inbox.findById(event.eventId()).chain(existing -> {
                    if (existing != null) {
                        span.setAttribute("inbox.duplicate", true);
                        return Uni.createFrom().voidItem();
                    }
                    return TraceContextSupport.inContext(span, action).chain(() -> {
                        InboxEvent done = new InboxEvent();
                        done.eventId = event.eventId();
                        done.type = event.type();
                        done.processedAt = OffsetDateTime.now(ZoneOffset.UTC);
                        return inbox.persist(done).replaceWithVoid();
                    });
                }))).onItemOrFailure().invoke((ignored, failure) -> {
                    if (failure != null) TraceContextSupport.fail(span, failure);
                    span.end();
                });
    }

    private Uni<Void> traceReservation(String name, String operation, EventEnvelope event,
                                       Supplier<Uni<Void>> action) {
        return TraceContextSupport.traceUni(name, SpanKind.INTERNAL, span -> {
            span.setAttribute("booking.id", event.correlationId().toString());
            span.setAttribute("event.id", event.eventId().toString());
            span.setAttribute("reservation.operation", operation);
            span.setAttribute("reservation.resource_type", "hotel");
        }, ignored -> action.get());
    }

    private Uni<Void> failure(EventPayloads.ReservationRequested request, EventEnvelope event, String reason) {
        Span.current().setAttribute("reservation.outcome", "FAILED")
                .setAttribute("reservation.failure_reason", reason);
        return raw(request.bookingId(), request.bookingItemId(), null, 0, request.currency(), "FAILED", reason,
                TopicNames.HOTEL_FAILED, event.eventId());
    }

    private Uni<Void> publish(HotelReservation reservation, UUID cause) {
        Span.current().setAttribute("reservation.outcome", reservation.status.name());
        String topic = switch (reservation.status) {
            case HELD -> TopicNames.HOTEL_HELD;
            case CONFIRMED -> TopicNames.HOTEL_CONFIRMED;
            case CANCELLED, EXPIRED -> TopicNames.HOTEL_CANCELLED;
        };
        return outcome(reservation, reservation.status.name(), null, topic, cause);
    }

    private Uni<Void> outcome(HotelReservation reservation, String status, String reason, String topic, UUID cause) {
        return raw(reservation.bookingId, reservation.bookingItemId, reservation.id, reservation.amountMinor,
                reservation.currency, status, reason, topic, cause);
    }

    private Uni<Void> raw(UUID bookingId, UUID itemId, UUID reservationId, long amount, String currency,
                          String status, String reason, String topic, UUID cause) {
        return outbox.enqueue(topic, bookingId, cause, new EventPayloads.ReservationOutcome(bookingId, itemId,
                reservationId, amount, currency, status, reason)).replaceWithVoid();
    }
}
