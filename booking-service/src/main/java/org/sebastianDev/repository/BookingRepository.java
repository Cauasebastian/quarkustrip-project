package org.sebastianDev.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.sebastianDev.model.Booking;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class BookingRepository implements PanacheRepository<Booking> {
    public Uni<Booking> findById(UUID id) {
        return find("id", id).firstResult();
    }

    public Uni<List<Booking>> findAll() {
        return listAll();
    }

    public Uni<Booking> update(Booking booking) {
        return persist(booking);
    }

    public Uni<Boolean> delete(UUID id) {
        return deleteById(id);
    }
}
