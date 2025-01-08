package org.sebastianDev.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.sebastianDev.model.HotelReservation;

import java.util.UUID;

@ApplicationScoped
public class HotelReservationRepository implements PanacheRepository<HotelReservation> {
    public HotelReservation findById(UUID id) {
        return find("id", id).firstResult();
    }

    public boolean deleteById(UUID id) {
        return delete("id", id) > 0;
    }
}