package org.sebastiandev.trip.hotel.repository;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase; import jakarta.enterprise.context.ApplicationScoped; import java.util.UUID; import org.sebastiandev.trip.hotel.domain.Hotel;
@ApplicationScoped public class HotelRepository implements PanacheRepositoryBase<Hotel, UUID> {}
