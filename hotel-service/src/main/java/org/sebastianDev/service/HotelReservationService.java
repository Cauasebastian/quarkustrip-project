package org.sebastianDev.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.sebastianDev.model.HotelReservation;
import org.sebastianDev.repository.HotelReservationRepository;
import org.sebastianDev.repository.RoomRepository;

import java.util.List;
import java.util.UUID;
@ApplicationScoped
public class HotelReservationService {

    @Inject
    HotelReservationRepository reservationRepository;

    @Inject
    RoomRepository roomRepository;

    public Uni<List<HotelReservation>> getAllReservations() {
        return reservationRepository.listAll();
    }

    public Uni<HotelReservation> getReservationById(UUID id) {
        return reservationRepository.findById(id);
    }

    public Uni<HotelReservation> createReservation(HotelReservation reservation) {
        return roomRepository.findById(reservation.room.id)
                .onItem().ifNotNull().transformToUni(room -> {
                    reservation.room = room;
                    return reservationRepository.persist(reservation).replaceWith(reservation);
                })
                .onItem().ifNull().failWith(new IllegalArgumentException("Quarto não encontrado para o ID: " + reservation.room.id));
    }

    public Uni<HotelReservation> updateReservation(UUID id, HotelReservation reservationDetails) {
        return reservationRepository.findById(id).onItem().ifNotNull().transformToUni(reservation -> {
            reservation.room = reservationDetails.room;
            reservation.bookingId = reservationDetails.bookingId;
            reservation.userId = reservationDetails.userId;
            reservation.checkInDate = reservationDetails.checkInDate;
            reservation.checkOutDate = reservationDetails.checkOutDate;
            reservation.status = reservationDetails.status;
            return reservationRepository.persist(reservation).replaceWith(reservation);
        });
    }

    public Uni<Boolean> deleteReservation(UUID id) {
        return reservationRepository.delete("id", id).map(count -> count > 0);
    }
}
