package org.sebastianDev.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.sebastianDev.model.HotelReservation;
import org.sebastianDev.repository.HotelReservationRepository;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class HotelReservationService {

    @Inject
    HotelReservationRepository reservationRepository;

    public List<HotelReservation> getAllReservations() {
        return reservationRepository.listAll();
    }

    public HotelReservation getReservationById(UUID id) {
        return reservationRepository.findById(id);
    }

    public HotelReservation createReservation(HotelReservation reservation) {
        reservationRepository.persist(reservation);
        return reservation;
    }

    public HotelReservation updateReservation(UUID id, HotelReservation reservationDetails) {
        HotelReservation reservation = reservationRepository.findById(id);
        if (reservation != null) {
            reservation.room = reservationDetails.room;
            reservation.bookingId = reservationDetails.bookingId;
            reservation.userId = reservationDetails.userId;
            reservation.checkInDate = reservationDetails.checkInDate;
            reservation.checkOutDate = reservationDetails.checkOutDate;
            reservation.status = reservationDetails.status;
            reservationRepository.persist(reservation);
        }
        return reservation;
    }

    public boolean deleteReservation(UUID id) {
        return reservationRepository.deleteById(id);
    }
}
