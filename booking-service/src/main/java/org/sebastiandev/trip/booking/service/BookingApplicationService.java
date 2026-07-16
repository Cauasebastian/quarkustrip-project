package org.sebastiandev.trip.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.sebastiandev.trip.booking.domain.Booking;
import org.sebastiandev.trip.booking.domain.BookingItem;
import org.sebastiandev.trip.booking.domain.BookingItemStatus;
import org.sebastiandev.trip.booking.domain.BookingItemType;
import org.sebastiandev.trip.booking.domain.BookingStatus;
import org.sebastiandev.trip.booking.messaging.OutboxService;
import org.sebastiandev.trip.booking.observability.SagaMetrics;
import org.sebastiandev.trip.booking.repository.BookingRepository;
import org.sebastiandev.trip.contracts.event.EventPayloads;
import org.sebastiandev.trip.contracts.event.TopicNames;
import org.sebastiandev.trip.contracts.grpc.BookingItemRequest;
import org.sebastiandev.trip.contracts.grpc.CreateBookingRequest;

@ApplicationScoped
public class BookingApplicationService {
    @Inject BookingRepository repository;
    @Inject BookingValidator validator;
    @Inject ObjectMapper mapper;
    @Inject OutboxService outbox;
    @Inject SagaMetrics metrics;

    public Uni<Booking> create(CreateBookingRequest request) {
        UUID userId = validator.validate(request);
        return Panache.withTransaction(() -> repository.find("idempotencyKey", request.getIdempotencyKey())
                .firstResult()
                .onItem().ifNull().switchTo(() -> persistNew(request, userId)));
    }

    private Uni<Booking> persistNew(CreateBookingRequest request, UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Booking booking = new Booking();
        booking.id = UUID.randomUUID();
        booking.userId = userId;
        booking.status = BookingStatus.RESERVING;
        booking.currency = request.getCurrency().toUpperCase();
        booking.paymentMethodRef = request.getPaymentMethodRef();
        booking.idempotencyKey = request.getIdempotencyKey();
        booking.stepDeadline = now.plusSeconds(60);
        booking.sagaDeadline = now.plusMinutes(5);
        booking.createdAt = now;
        booking.updatedAt = now;

        for (BookingItemRequest requested : request.getItemsList()) {
            BookingItem item = new BookingItem();
            item.id = UUID.randomUUID();
            item.booking = booking;
            item.status = BookingItemStatus.PENDING;
            item.createdAt = now;
            item.updatedAt = now;
            Map<String, String> attributes = new HashMap<>();
            switch (requested.getItemCase()) {
                case FLIGHT -> {
                    item.type = BookingItemType.FLIGHT;
                    item.resourceId = validator.parseUuid(requested.getFlight().getFlightId(), "flightId");
                    attributes.put("seatNumber", requested.getFlight().getSeatNumber());
                }
                case HOTEL -> {
                    item.type = BookingItemType.HOTEL;
                    item.resourceId = validator.parseUuid(requested.getHotel().getRoomId(), "roomId");
                    attributes.put("checkIn", localDate(requested.getHotel().getCheckIn()));
                    attributes.put("checkOut", localDate(requested.getHotel().getCheckOut()));
                }
                case TRANSPORT -> {
                    item.type = BookingItemType.TRANSPORT;
                    item.resourceId = validator.parseUuid(requested.getTransport().getTransportId(), "transportId");
                    attributes.put("startsAt", timestamp(requested.getTransport().getStartsAt()));
                    attributes.put("endsAt", timestamp(requested.getTransport().getEndsAt()));
                }
                default -> throw new IllegalArgumentException("unsupported booking item");
            }
            try {
                item.requestData = mapper.writeValueAsString(attributes);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("could not serialize booking item", exception);
            }
            booking.items.add(item);
        }

        return repository.persist(booking)
                .chain(() -> outbox.enqueue(TopicNames.BOOKING_CREATED, booking.id, null,
                        new EventPayloads.BookingTerminal(booking.id, booking.userId, booking.status.name(), 0,
                                booking.currency, null)))
                .chain(() -> enqueueReserves(booking, null))
                .replaceWith(booking);
    }

    public Uni<Booking> get(UUID id, UUID requesterId, boolean admin) {
        return repository.findById(id).onItem().ifNull().failWith(() -> new IllegalArgumentException("booking not found"))
                .invoke(booking -> {
                    if (!admin && !booking.userId.equals(requesterId)) throw new SecurityException("booking belongs to another user");
                });
    }

    public Uni<BookingPage> list(UUID userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Uni<Long> total = repository.count("userId", userId);
        Uni<List<Booking>> bookings = repository.find("userId", Sort.descending("createdAt"), userId)
                .page(Page.of(safePage, safeSize))
                .list();
        return Uni.combine().all().unis(bookings, total).asTuple()
                .map(result -> new BookingPage(result.getItem1(), safePage, safeSize, result.getItem2()));
    }

    public record BookingPage(List<Booking> bookings, int page, int size, long totalElements) {
    }

    public Uni<Booking> cancel(UUID id, UUID requesterId, boolean admin, String reason) {
        return Panache.withTransaction(() -> get(id, requesterId, admin).chain(booking -> {
            if (booking.status == BookingStatus.CANCELLED) return Uni.createFrom().item(booking);
            if (booking.status == BookingStatus.FAILED || booking.status == BookingStatus.MANUAL_REVIEW) {
                throw new IllegalStateException("booking cannot be cancelled in state " + booking.status);
            }
            booking.cancellationRequested = true;
            return startCompensation(booking, reason == null || reason.isBlank() ? "USER_CANCELLED" : reason, null)
                    .replaceWith(booking);
        }));
    }

    private Uni<Void> enqueueReserves(Booking booking, UUID causationId) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (BookingItem item : booking.items) {
            chain = chain.chain(() -> enqueueReserve(booking, item, causationId));
        }
        return chain;
    }

    Uni<Void> enqueueReserve(Booking booking, BookingItem item, UUID causationId) {
        Map<String, String> attributes;
        try {
            attributes = mapper.readValue(item.requestData, mapper.getTypeFactory()
                    .constructMapType(Map.class, String.class, String.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
        return outbox.enqueue(topic(item.type, "reserve"), booking.id, causationId,
                new EventPayloads.ReservationRequested(booking.id, item.id, booking.userId, item.resourceId,
                        attributes, booking.currency, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15)))
                .replaceWithVoid();
    }

    public Uni<Void> startPayment(Booking booking, UUID causationId) {
        booking.status = BookingStatus.PAYMENT_PENDING;
        booking.totalAmountMinor = booking.items.stream().mapToLong(item -> item.amountMinor).sum();
        booking.stepDeadline = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(60);
        booking.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        metrics.transition(BookingStatus.PAYMENT_PENDING.name());
        return outbox.enqueue(TopicNames.PAYMENT_PROCESS_REQUESTED, booking.id, causationId,
                new EventPayloads.PaymentRequested(booking.id, booking.userId, booking.totalAmountMinor,
                        booking.currency, booking.paymentMethodRef)).replaceWithVoid();
    }

    public Uni<Void> startConfirmation(Booking booking, UUID causationId) {
        booking.status = BookingStatus.CONFIRMING;
        booking.stepDeadline = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(60);
        booking.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        metrics.transition(BookingStatus.CONFIRMING.name());
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (BookingItem item : booking.items) {
            chain = chain.chain(() -> outbox.enqueue(topic(item.type, "confirm"), booking.id, causationId,
                    new EventPayloads.ReservationAction(booking.id, item.id, item.reservationId)).replaceWithVoid());
        }
        return chain;
    }

    public Uni<Void> startCompensation(Booking booking, String reason, UUID causationId) {
        booking.status = BookingStatus.COMPENSATING;
        booking.failureCode = reason;
        booking.stepDeadline = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(60);
        booking.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        metrics.compensation();
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (BookingItem item : booking.items) {
            if (item.reservationId != null && item.status != BookingItemStatus.CANCELLED) {
                chain = chain.chain(() -> outbox.enqueue(topic(item.type, "cancel"), booking.id, causationId,
                        new EventPayloads.ReservationAction(booking.id, item.id, item.reservationId)).replaceWithVoid());
            }
        }
        if (booking.paymentId != null) {
            chain = chain.chain(() -> outbox.enqueue(TopicNames.PAYMENT_REFUND_REQUESTED, booking.id, causationId,
                    new EventPayloads.RefundRequested(booking.id, booking.paymentId, reason)).replaceWithVoid());
        }
        return chain.chain(() -> finishCompensationIfPossible(booking, causationId));
    }

    public Uni<Void> finishCompensationIfPossible(Booking booking, UUID causationId) {
        boolean resourcesReleased = booking.items.stream().allMatch(item ->
                item.status == BookingItemStatus.CANCELLED || item.status == BookingItemStatus.FAILED);
        if (resourcesReleased && booking.paymentId == null) {
            booking.status = booking.cancellationRequested ? BookingStatus.CANCELLED : BookingStatus.FAILED;
            booking.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
            metrics.terminal(booking);
            String topic = booking.status == BookingStatus.CANCELLED ? TopicNames.BOOKING_CANCELLED : TopicNames.BOOKING_FAILED;
            return outbox.enqueue(topic, booking.id, causationId, terminal(booking)).replaceWithVoid();
        }
        return Uni.createFrom().voidItem();
    }

    public EventPayloads.BookingTerminal terminal(Booking booking) {
        return new EventPayloads.BookingTerminal(booking.id, booking.userId, booking.status.name(),
                booking.totalAmountMinor, booking.currency, booking.failureCode);
    }

    private String topic(BookingItemType type, String action) {
        return switch (type) {
            case FLIGHT -> switch (action) {
                case "reserve" -> TopicNames.FLIGHT_RESERVE_REQUESTED;
                case "confirm" -> TopicNames.FLIGHT_CONFIRM_REQUESTED;
                default -> TopicNames.FLIGHT_CANCEL_REQUESTED;
            };
            case HOTEL -> switch (action) {
                case "reserve" -> TopicNames.HOTEL_RESERVE_REQUESTED;
                case "confirm" -> TopicNames.HOTEL_CONFIRM_REQUESTED;
                default -> TopicNames.HOTEL_CANCEL_REQUESTED;
            };
            case TRANSPORT -> switch (action) {
                case "reserve" -> TopicNames.TRANSPORT_RESERVE_REQUESTED;
                case "confirm" -> TopicNames.TRANSPORT_CONFIRM_REQUESTED;
                default -> TopicNames.TRANSPORT_CANCEL_REQUESTED;
            };
        };
    }

    private String localDate(org.sebastiandev.trip.contracts.grpc.LocalDateValue value) {
        return java.time.LocalDate.of(value.getYear(), value.getMonth(), value.getDay()).toString();
    }

    private String timestamp(com.google.protobuf.Timestamp value) {
        return java.time.Instant.ofEpochSecond(value.getSeconds(), value.getNanos()).toString();
    }
}
