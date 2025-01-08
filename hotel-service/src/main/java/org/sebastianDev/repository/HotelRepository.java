package org.sebastianDev.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.sebastianDev.model.Hotel;

import java.util.UUID;

@ApplicationScoped
public class HotelRepository implements PanacheRepository<Hotel> {
    public Hotel findById(UUID id) {
        return find("id", id).firstResult();
    }

    public boolean deleteById(UUID id) {
        return delete("id", id) > 0;
    }

    // Outros métodos de consulta personalizados podem ser adicionados aqui
}