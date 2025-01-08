package org.sebastianDev.repository;



import io.quarkus.hibernate.orm.panache.PanacheRepository;
import org.sebastianDev.model.Room;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class RoomRepository implements PanacheRepository<Room> {
    public Room findById(UUID id) {
        return find("id", id).firstResult();
    }

    public boolean deleteById(UUID id) {
        return delete("id", id) > 0;
    }

    // Outros métodos de consulta personalizados podem ser adicionados aqui
}