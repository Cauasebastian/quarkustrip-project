package org.sebastianDev.service;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.sebastianDev.model.Hotel;
import org.sebastianDev.repository.HotelRepository;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class HotelService {

    @Inject
    HotelRepository hotelRepository;

    public Uni<List<Hotel>> getAllHotels() {
        return hotelRepository.listAll();
    }

    public Uni<Hotel> getHotelById(UUID id) {
        return hotelRepository.findById(id);
    }

    public Uni<Hotel> createHotel(Hotel hotel) {
        return Panache.withTransaction(() ->
                hotelRepository.persist(hotel)
        ).replaceWith(hotel);
    }

    public Uni<Hotel> updateHotel(UUID id, Hotel hotelDetails) {
        return Panache.withTransaction(() ->
                hotelRepository.findById(id)
                        .onItem().ifNull().failWith(new NotFoundException("Hotel não encontrado"))
                        .invoke(hotel -> {
                            hotel.name = hotelDetails.name;
                            hotel.address = hotelDetails.address;
                            hotel.city = hotelDetails.city;
                            hotel.country = hotelDetails.country;
                            hotel.rating = hotelDetails.rating;
                        })
                        .call(hotel -> hotelRepository.persist(hotel))
        );
    }

    public Uni<Boolean> deleteHotel(UUID id) {
        return Panache.withTransaction(() ->
                hotelRepository.delete("id", id)
                        .map(count -> count > 0)
        );
    }
}
