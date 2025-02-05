package org.sebastianDev.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.sebastianDev.model.FlightReservation;

import java.util.UUID;

@ApplicationScoped
public class FlightReservationRepository implements PanacheRepository<FlightReservation> {
    public Uni<FlightReservation> findById(UUID id) {
        return find("id", id).firstResult();
    }
}