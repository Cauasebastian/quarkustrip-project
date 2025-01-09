package org.sebastianDev.repository;



import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import org.sebastianDev.model.Room;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;


@ApplicationScoped
public class RoomRepository implements PanacheRepository<Room> {
    public Uni<Room> findById(UUID id) {
        return find("id", id).firstResult();
    }
}