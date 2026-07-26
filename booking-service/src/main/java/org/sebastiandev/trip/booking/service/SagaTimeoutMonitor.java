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
import org.sebastiandev.trip.booking.repository.BookingRepository;
import org.sebastiandev.trip.contracts.observability.TraceContextSnapshot;
import org.sebastiandev.trip.contracts.observability.TraceContextSupport;

@ApplicationScoped
public class SagaTimeoutMonitor {
    @Inject BookingRepository bookings;
    @Inject BookingApplicationService service;

    @Scheduled(every = "${trip.saga.timeout-check-interval:5s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    Uni<Void> expire() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return Panache.withSession(() ->
                        bookings.find("status not in (?1, ?2, ?3, ?4) and (stepDeadline < ?5 or sagaDeadline < ?5)",
                                        BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.FAILED,
                                        BookingStatus.MANUAL_REVIEW, now)
                                .list().map(list -> list.stream().map(booking -> new TimeoutCandidate(booking.id,
                                                new TraceContextSnapshot(booking.sagaTraceParent,
                                                        booking.sagaTraceState)))
                                        .toList()))
                .chain(this::expireSequentially);
    }

    private Uni<Void> expireSequentially(List<TimeoutCandidate> candidates) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (TimeoutCandidate candidate : candidates) {
            chain = chain.chain(() -> TraceContextSupport.inContext(TraceContextSupport.restore(candidate.trace()),
                    () -> Panache.withTransaction(() -> service.lockBooking(candidate.id(), null).chain(booking -> {
                if (booking == null || booking.status == BookingStatus.CONFIRMED
                        || booking.status == BookingStatus.CANCELLED || booking.status == BookingStatus.FAILED
                        || booking.status == BookingStatus.MANUAL_REVIEW) return Uni.createFrom().voidItem();
                OffsetDateTime lockedAt = OffsetDateTime.now(ZoneOffset.UTC);
                if (!booking.stepDeadline.isBefore(lockedAt) && !booking.sagaDeadline.isBefore(lockedAt)) {
                    return Uni.createFrom().voidItem();
                }
                if (booking.status == BookingStatus.COMPENSATING) {
                    return service.inSagaContext(booking,
                            () -> service.manualReview(booking, "COMPENSATION_TIMEOUT", null));
                }
                return service.inSagaContext(booking, () -> service.startCompensation(booking, "SAGA_TIMEOUT", null));
            }))));
        }
        return chain;
    }

    private record TimeoutCandidate(UUID id, TraceContextSnapshot trace) {
    }
}
