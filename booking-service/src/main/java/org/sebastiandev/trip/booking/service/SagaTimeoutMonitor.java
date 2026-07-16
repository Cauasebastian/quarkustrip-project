package org.sebastiandev.trip.booking.service;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.sebastiandev.trip.booking.domain.BookingStatus;
import org.sebastiandev.trip.booking.messaging.OutboxService;
import org.sebastiandev.trip.booking.repository.BookingRepository;
import org.sebastiandev.trip.contracts.event.TopicNames;

@ApplicationScoped
public class SagaTimeoutMonitor {
    @Inject BookingRepository bookings;
    @Inject BookingApplicationService service;
    @Inject OutboxService outbox;

    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    Uni<Void> expire() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return bookings.find("status not in (?1, ?2, ?3, ?4) and (stepDeadline < ?5 or sagaDeadline < ?5)",
                        BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.FAILED,
                        BookingStatus.MANUAL_REVIEW, now)
                .list().map(list -> list.stream().map(booking -> booking.id).toList())
                .chain(this::expireSequentially);
    }

    private Uni<Void> expireSequentially(List<UUID> ids) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (UUID id : ids) {
            chain = chain.chain(() -> Panache.withTransaction(() -> bookings.findById(id).chain(booking -> {
                if (booking == null || booking.status == BookingStatus.CONFIRMED
                        || booking.status == BookingStatus.CANCELLED || booking.status == BookingStatus.FAILED
                        || booking.status == BookingStatus.MANUAL_REVIEW) return Uni.createFrom().voidItem();
                if (booking.status == BookingStatus.COMPENSATING) {
                    booking.status = BookingStatus.MANUAL_REVIEW;
                    booking.failureCode = "COMPENSATION_TIMEOUT";
                    return outbox.enqueue(TopicNames.BOOKING_MANUAL_REVIEW, booking.id, null,
                            service.terminal(booking)).replaceWithVoid();
                }
                return service.startCompensation(booking, "SAGA_TIMEOUT", null);
            })));
        }
        return chain;
    }
}
