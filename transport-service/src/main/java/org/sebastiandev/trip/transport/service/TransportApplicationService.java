package org.sebastiandev.trip.transport.service;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.event.EventPayloads;
import org.sebastiandev.trip.contracts.event.TopicNames;
import org.sebastiandev.trip.transport.domain.TransportReservation;
import org.sebastiandev.trip.transport.messaging.InboxEvent;
import org.sebastiandev.trip.transport.messaging.OutboxService;
import org.sebastiandev.trip.transport.repository.InboxRepository;
import org.sebastiandev.trip.transport.repository.TransportOfferRepository;
import org.sebastiandev.trip.transport.repository.TransportReservationRepository;

@ApplicationScoped
public class TransportApplicationService {
    @Inject TransportOfferRepository offers;
    @Inject TransportReservationRepository reservations;
    @Inject InboxRepository inbox;
    @Inject OutboxService outbox;

    public Uni<Void> reserve(EventEnvelope event, EventPayloads.ReservationRequested request) {
        return process(event, () -> reservations.find("bookingItemId", request.bookingItemId()).firstResult()
                .chain(existing -> existing == null ? hold(request, event) : publish(existing, event.eventId())));
    }

    public Uni<Void> confirm(EventEnvelope event, EventPayloads.ReservationAction request) {
        return process(event, () -> reservations.findById(request.reservationId()).chain(reservation -> {
            if (reservation == null || !reservation.bookingItemId.equals(request.bookingItemId())) {
                return raw(request.bookingId(), request.bookingItemId(), request.reservationId(), 0, "XXX", "FAILED",
                        "RESERVATION_NOT_FOUND", TopicNames.TRANSPORT_FAILED, event.eventId());
            }
            if (reservation.status == TransportReservation.Status.CONFIRMED) {
                return publish(reservation, event.eventId());
            }
            if (reservation.status != TransportReservation.Status.HELD
                    || reservation.holdUntil.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
                return outcome(reservation, "FAILED", "HOLD_EXPIRED", TopicNames.TRANSPORT_FAILED, event.eventId());
            }
            reservation.status = TransportReservation.Status.CONFIRMED;
            reservation.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
            return publish(reservation, event.eventId());
        }));
    }

    public Uni<Void> cancel(EventEnvelope event, EventPayloads.ReservationAction request) {
        return process(event, () -> reservations.findById(request.reservationId()).chain(reservation -> {
            if (reservation == null) {
                return raw(request.bookingId(), request.bookingItemId(), request.reservationId(), 0, "XXX",
                        "CANCELLED", null, TopicNames.TRANSPORT_CANCELLED, event.eventId());
            }
            reservation.status = TransportReservation.Status.CANCELLED;
            reservation.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
            return publish(reservation, event.eventId());
        }));
    }

    private Uni<Void> hold(EventPayloads.ReservationRequested request, EventEnvelope event) {
        OffsetDateTime starts;
        OffsetDateTime ends;
        try {
            starts = OffsetDateTime.ofInstant(Instant.parse(request.attributes().get("startsAt")), ZoneOffset.UTC);
            ends = OffsetDateTime.ofInstant(Instant.parse(request.attributes().get("endsAt")), ZoneOffset.UTC);
        } catch (RuntimeException exception) {
            return failure(request, event, "INVALID_INTERVAL");
        }
        if (!ends.isAfter(starts)) return failure(request, event, "INVALID_INTERVAL");
        OffsetDateTime start = starts;
        OffsetDateTime end = ends;
        return offers.findById(request.resourceId(), LockModeType.PESSIMISTIC_WRITE).chain(offer -> {
            if (offer == null || !offer.active) return failure(request, event, "TRANSPORT_NOT_FOUND");
            return reservations.count("offerId = ?1 and status in (?2, ?3) and startsAt < ?4 and endsAt > ?5",
                            request.resourceId(), TransportReservation.Status.HELD,
                            TransportReservation.Status.CONFIRMED, end, start)
                    .chain(conflicts -> {
                        if (conflicts > 0) return failure(request, event, "TRANSPORT_UNAVAILABLE");
                        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                        TransportReservation reservation = new TransportReservation();
                        reservation.id = UUID.randomUUID();
                        reservation.bookingId = request.bookingId();
                        reservation.bookingItemId = request.bookingItemId();
                        reservation.userId = request.userId();
                        reservation.offerId = request.resourceId();
                        reservation.startsAt = start;
                        reservation.endsAt = end;
                        reservation.status = TransportReservation.Status.HELD;
                        reservation.amountMinor = offer.priceMinor;
                        reservation.currency = offer.currency;
                        reservation.holdUntil = request.holdUntil();
                        reservation.createdAt = now;
                        reservation.updatedAt = now;
                        return reservations.persist(reservation).chain(() -> publish(reservation, event.eventId()));
                    });
        });
    }

    private Uni<Void> process(EventEnvelope event, Supplier<Uni<Void>> action) {
        return Panache.withTransaction(() -> inbox.findById(event.eventId()).chain(existing -> {
            if (existing != null) return Uni.createFrom().voidItem();
            return action.get().chain(() -> {
                InboxEvent done = new InboxEvent();
                done.eventId = event.eventId();
                done.type = event.type();
                done.processedAt = OffsetDateTime.now(ZoneOffset.UTC);
                return inbox.persist(done).replaceWithVoid();
            });
        }));
    }

    private Uni<Void> failure(EventPayloads.ReservationRequested request, EventEnvelope event, String reason) {
        return raw(request.bookingId(), request.bookingItemId(), null, 0, request.currency(), "FAILED", reason,
                TopicNames.TRANSPORT_FAILED, event.eventId());
    }

    private Uni<Void> publish(TransportReservation reservation, UUID cause) {
        String topic = switch (reservation.status) {
            case HELD -> TopicNames.TRANSPORT_HELD;
            case CONFIRMED -> TopicNames.TRANSPORT_CONFIRMED;
            case CANCELLED, EXPIRED -> TopicNames.TRANSPORT_CANCELLED;
        };
        return outcome(reservation, reservation.status.name(), null, topic, cause);
    }

    private Uni<Void> outcome(TransportReservation reservation, String status, String reason, String topic,
                              UUID cause) {
        return raw(reservation.bookingId, reservation.bookingItemId, reservation.id, reservation.amountMinor,
                reservation.currency, status, reason, topic, cause);
    }

    private Uni<Void> raw(UUID bookingId, UUID itemId, UUID reservationId, long amount, String currency,
                          String status, String reason, String topic, UUID cause) {
        return outbox.enqueue(topic, bookingId, cause, new EventPayloads.ReservationOutcome(bookingId, itemId,
                reservationId, amount, currency, status, reason)).replaceWithVoid();
    }
}
