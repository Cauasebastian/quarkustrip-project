package org.sebastiandev.trip.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.NullNode;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.sebastiandev.trip.booking.domain.Booking;
import org.sebastiandev.trip.booking.domain.BookingItem;
import org.sebastiandev.trip.booking.domain.BookingItemStatus;
import org.sebastiandev.trip.booking.domain.BookingItemType;
import org.sebastiandev.trip.booking.domain.BookingStatus;
import org.sebastiandev.trip.booking.domain.PaymentState;
import org.sebastiandev.trip.booking.repository.BookingRepository;
import org.sebastiandev.trip.booking.repository.OutboxRepository;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.event.EventPayloads;
import org.sebastiandev.trip.contracts.event.TopicNames;

@QuarkusTest
@QuarkusTestResource(BookingPostgresTestResource.class)
class BookingConcurrencyIT {
    @Inject BookingApplicationService service;
    @Inject BookingRepository bookings;
    @Inject OutboxRepository outbox;
    @Inject SagaTimeoutMonitor timeoutMonitor;

    @Test
    @RunOnVertxContext
    void simultaneousHoldsRequestPaymentOnlyOnce(UniAsserter asserter) {
        AtomicReference<Booking> fixture = new AtomicReference<>();
        asserter.execute(() -> seed(BookingStatus.RESERVING, BookingItemStatus.PENDING, false)
                .invoke(fixture::set).replaceWithVoid());
        asserter.execute(() -> parallelOutcomes(fixture.get(), "HELD"));
        asserter.assertThat(() -> result(fixture.get().id, TopicNames.PAYMENT_PROCESS_REQUESTED), result -> {
            assertEquals(BookingStatus.PAYMENT_PENDING, result.status());
            assertEquals(1L, result.outboxCount());
        });
    }

    @Test
    @RunOnVertxContext
    void simultaneousConfirmationsPublishOneTerminalEvent(UniAsserter asserter) {
        AtomicReference<Booking> fixture = new AtomicReference<>();
        asserter.execute(() -> seed(BookingStatus.CONFIRMING, BookingItemStatus.HELD, false)
                .invoke(fixture::set).replaceWithVoid());
        asserter.execute(() -> parallelOutcomes(fixture.get(), "CONFIRMED"));
        asserter.assertThat(() -> result(fixture.get().id, TopicNames.BOOKING_CONFIRMED), result -> {
            assertEquals(BookingStatus.CONFIRMED, result.status());
            assertEquals(1L, result.outboxCount());
        });
    }

    @Test
    @RunOnVertxContext
    void simultaneousCancellationsFinishCompensationOnlyOnce(UniAsserter asserter) {
        AtomicReference<Booking> fixture = new AtomicReference<>();
        asserter.execute(() -> seed(BookingStatus.COMPENSATING, BookingItemStatus.HELD, false, booking -> {
                    booking.cancellationRequested = true;
                    booking.paymentState = PaymentState.NOT_REQUESTED;
                }).invoke(fixture::set).replaceWithVoid());
        asserter.execute(() -> parallelOutcomes(fixture.get(), "CANCELLED"));
        asserter.assertThat(() -> result(fixture.get().id, TopicNames.BOOKING_CANCELLED), result -> {
            assertEquals(BookingStatus.CANCELLED, result.status());
            assertEquals(1L, result.outboxCount());
        });
    }

    @Test
    @RunOnVertxContext
    void duplicateEventDoesNotRepeatPaymentRequest(UniAsserter asserter) {
        AtomicReference<Booking> fixture = new AtomicReference<>();
        AtomicReference<EventEnvelope> event = new AtomicReference<>();
        asserter.execute(() -> seed(BookingStatus.RESERVING, BookingItemStatus.PENDING, false,
                        booking -> booking.items.subList(1, booking.items.size()).clear())
                .invoke(booking -> {
                    fixture.set(booking);
                    event.set(envelope(booking.id, TopicNames.FLIGHT_HELD));
                }).replaceWithVoid());
        asserter.execute(() -> {
            Booking booking = fixture.get();
            BookingItem item = booking.items.getFirst();
            EventPayloads.ReservationOutcome payload = outcome(booking, item, "HELD");
            return Uni.combine().all().unis(
                    isolated(() -> service.processReservationOutcome(event.get(), payload)),
                    isolated(() -> service.processReservationOutcome(event.get(), payload))).discardItems();
        });
        asserter.assertThat(() -> result(fixture.get().id, TopicNames.PAYMENT_PROCESS_REQUESTED), result -> {
            assertEquals(BookingStatus.PAYMENT_PENDING, result.status());
            assertEquals(1L, result.outboxCount());
        });
    }

    @Test
    @RunOnVertxContext
    void timeoutRacingTheLastConfirmationNeverProducesDuplicateTerminalEvents(UniAsserter asserter) {
        AtomicReference<Booking> fixture = new AtomicReference<>();
        asserter.execute(() -> seed(BookingStatus.CONFIRMING, BookingItemStatus.CONFIRMED, true,
                        booking -> booking.items.getLast().status = BookingItemStatus.HELD)
                .invoke(fixture::set).replaceWithVoid());
        asserter.execute(() -> {
            Booking booking = fixture.get();
            BookingItem last = booking.items.getLast();
            return Uni.combine().all().unis(
                    isolated(() -> service.processReservationOutcome(
                            envelope(booking.id, TopicNames.TRANSPORT_CONFIRMED),
                            outcome(booking, last, "CONFIRMED"))),
                    isolated(timeoutMonitor::expire)).discardItems();
        });
        asserter.assertThat(() -> terminalResult(fixture.get().id), result -> {
            assertTrue(Set.of(BookingStatus.CONFIRMED, BookingStatus.COMPENSATING).contains(result.status()));
            assertTrue(result.outboxCount() <= 1L);
        });
    }

    private Uni<Void> parallelOutcomes(Booking booking, String status) {
        List<Uni<Void>> operations = new ArrayList<>();
        for (BookingItem item : booking.items) {
            String topic = switch (item.type) {
                case FLIGHT -> status.equals("HELD") ? TopicNames.FLIGHT_HELD
                        : status.equals("CONFIRMED") ? TopicNames.FLIGHT_CONFIRMED : TopicNames.FLIGHT_CANCELLED;
                case HOTEL -> status.equals("HELD") ? TopicNames.HOTEL_HELD
                        : status.equals("CONFIRMED") ? TopicNames.HOTEL_CONFIRMED : TopicNames.HOTEL_CANCELLED;
                case TRANSPORT -> status.equals("HELD") ? TopicNames.TRANSPORT_HELD
                        : status.equals("CONFIRMED") ? TopicNames.TRANSPORT_CONFIRMED
                        : TopicNames.TRANSPORT_CANCELLED;
            };
            operations.add(isolated(() -> service.processReservationOutcome(envelope(booking.id, topic),
                    outcome(booking, item, status))));
        }
        return Uni.combine().all().unis(operations).discardItems();
    }

    private Uni<Void> isolated(Supplier<Uni<Void>> operation) {
        return Uni.createFrom().emitter(emitter -> {
            var context = VertxContext.createNewDuplicatedContext();
            VertxContextSafetyToggle.setContextSafe(context, true);
            context.runOnContext(ignored -> {
                try {
                    operation.get().subscribe().with(emitter::complete, emitter::fail);
                } catch (Throwable failure) {
                    emitter.fail(failure);
                }
            });
        });
    }

    private EventPayloads.ReservationOutcome outcome(Booking booking, BookingItem item, String status) {
        UUID reservationId = item.reservationId == null ? UUID.randomUUID() : item.reservationId;
        return new EventPayloads.ReservationOutcome(booking.id, item.id, reservationId,
                10_000L, "BRL", status, null);
    }

    private EventEnvelope envelope(UUID bookingId, String type) {
        return new EventEnvelope(UUID.randomUUID(), type, 1, OffsetDateTime.now(ZoneOffset.UTC),
                bookingId, null, "resilience-test", NullNode.getInstance());
    }

    private Uni<Booking> seed(BookingStatus status, BookingItemStatus itemStatus, boolean expired) {
        return seed(status, itemStatus, expired, ignored -> {
        });
    }

    private Uni<Booking> seed(BookingStatus status, BookingItemStatus itemStatus, boolean expired,
                              Consumer<Booking> customize) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Booking booking = new Booking();
        booking.id = UUID.randomUUID();
        booking.userId = UUID.randomUUID();
        booking.createdByUserId = booking.userId;
        booking.status = status;
        booking.currency = "BRL";
        booking.totalAmountMinor = status == BookingStatus.RESERVING ? 0 : 30_000L;
        booking.paymentMethodRef = "pm_test_success";
        booking.paymentState = status == BookingStatus.RESERVING
                ? PaymentState.NOT_REQUESTED : PaymentState.SUCCEEDED;
        booking.idempotencyKey = UUID.randomUUID().toString();
        booking.stepDeadline = expired ? now.minusSeconds(1) : now.plusMinutes(5);
        booking.sagaDeadline = now.plusMinutes(10);
        booking.createdAt = now;
        booking.updatedAt = now;

        BookingItemType[] types = BookingItemType.values();
        for (int index = 0; index < types.length; index++) {
            BookingItem item = new BookingItem();
            item.id = UUID.randomUUID();
            item.booking = booking;
            item.type = types[index];
            item.status = itemStatus;
            item.resourceId = UUID.randomUUID();
            item.requestData = "{}";
            item.reservationId = itemStatus == BookingItemStatus.PENDING ? null : UUID.randomUUID();
            item.amountMinor = itemStatus == BookingItemStatus.PENDING ? 0 : 10_000L;
            item.createdAt = now;
            item.updatedAt = now;
            booking.items.add(item);
        }
        customize.accept(booking);
        return Panache.withTransaction(() -> bookings.persist(booking).replaceWith(booking));
    }

    private Uni<Result> result(UUID bookingId, String topic) {
        return Panache.withTransaction(() -> bookings.findById(bookingId)
                .chain(booking -> outbox.count("aggregateId = ?1 and topic = ?2", bookingId, topic)
                        .map(count -> new Result(booking.status, count))));
    }

    private Uni<Result> terminalResult(UUID bookingId) {
        return Panache.withTransaction(() -> bookings.findById(bookingId).chain(booking ->
                outbox.count("aggregateId = ?1 and topic in (?2, ?3)", bookingId,
                                TopicNames.BOOKING_CONFIRMED, TopicNames.BOOKING_CANCELLED)
                        .map(count -> new Result(booking.status, count))));
    }

    private record Result(BookingStatus status, long outboxCount) {
    }
}
