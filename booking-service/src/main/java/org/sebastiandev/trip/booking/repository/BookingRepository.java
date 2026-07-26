package org.sebastiandev.trip.booking.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.sebastiandev.trip.booking.domain.Booking;

@ApplicationScoped
public class BookingRepository implements PanacheRepositoryBase<Booking, UUID> {
    public Uni<Booking> findByIdForUpdate(UUID id) {
        return find("id", id).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
    }
}
