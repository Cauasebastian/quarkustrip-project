package org.sebastianDev.service;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
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
        return Panache.withTransaction(() ->
                roomRepository.findById(reservation.room.id)
                        .onItem().ifNull().failWith(new NotFoundException("Quarto não encontrado (ID: " + reservation.room.id + ")"))
                        .invoke(room -> reservation.room = room)
                        .call(() -> reservationRepository.persist(reservation))
        ).replaceWith(reservation);
    }

    public Uni<HotelReservation> updateReservation(UUID id, HotelReservation reservationDetails) {
        return Panache.withTransaction(() ->
                reservationRepository.findById(id)
                        .onItem().ifNull().failWith(new NotFoundException("Reserva não encontrada (ID: " + id + ")"))
                        .invoke(reservation -> {
                            reservation.room = reservationDetails.room;
                            reservation.bookingId = reservationDetails.bookingId;
                            reservation.userId = reservationDetails.userId;
                            reservation.checkInDate = reservationDetails.checkInDate;
                            reservation.checkOutDate = reservationDetails.checkOutDate;
                            reservation.status = reservationDetails.status;
                        })
                        .call(reservation -> reservationRepository.persist(reservation))
        );
    }

    public Uni<Boolean> deleteReservation(UUID id) {
        return Panache.withTransaction(() ->
                reservationRepository.delete("id", id)
                        .map(count -> count > 0)
        );
    }
}
