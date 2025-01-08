package org.sebastianDev.service;


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

    public List<Hotel> getAllHotels() {
        return hotelRepository.listAll();
    }

    public Hotel getHotelById(UUID id) {
        return hotelRepository.findById(id);
    }

    public Hotel createHotel(Hotel hotel) {
        hotelRepository.persist(hotel);
        return hotel;
    }

    public Hotel updateHotel(UUID id, Hotel hotelDetails) {
        Hotel hotel = hotelRepository.findById(id);
        if (hotel != null) {
            hotel.name = hotelDetails.name;
            hotel.address = hotelDetails.address;
            hotel.city = hotelDetails.city;
            hotel.country = hotelDetails.country;
            hotel.rating = hotelDetails.rating;
            hotelRepository.persist(hotel);
        }
        return hotel;
    }

    public boolean deleteHotel(UUID id) {
        return hotelRepository.deleteById(id);
    }
}
