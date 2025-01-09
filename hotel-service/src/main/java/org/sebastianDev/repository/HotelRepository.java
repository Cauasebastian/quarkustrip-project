package org.sebastianDev.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.sebastianDev.model.Hotel;

import java.util.UUID;

@ApplicationScoped
public class HotelRepository implements PanacheRepository<Hotel> {
    public Uni<Hotel> findById(UUID id) {
        return find("id", id).firstResult();
    }
}
