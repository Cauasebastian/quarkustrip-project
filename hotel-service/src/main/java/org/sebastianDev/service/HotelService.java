package org.sebastianDev.service;


import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
        return hotelRepository.persist(hotel).replaceWith(hotel);
    }

    public Uni<Hotel> updateHotel(UUID id, Hotel hotelDetails) {
        return hotelRepository.findById(id).onItem().ifNotNull().transformToUni(hotel -> {
            hotel.name = hotelDetails.name;
            hotel.address = hotelDetails.address;
            hotel.city = hotelDetails.city;
            hotel.country = hotelDetails.country;
            hotel.rating = hotelDetails.rating;
            return hotelRepository.persist(hotel).replaceWith(hotel);
        });
    }

    public Uni<Boolean> deleteHotel(UUID id) {
        return hotelRepository.delete("id", id).map(count -> count > 0);
    }
}
