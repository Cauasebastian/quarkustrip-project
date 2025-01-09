package org.sebastianDev.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.sebastianDev.model.HotelReservation;

import java.util.UUID;

@ApplicationScoped
public class HotelReservationRepository implements PanacheRepository<HotelReservation> {
    public Uni<HotelReservation> findById(UUID id) {
        return find("id", id).firstResult();
    }
}
