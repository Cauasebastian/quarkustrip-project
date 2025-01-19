package org.sebastianDev.service;


import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.sebastianDev.model.Booking;
import org.sebastianDev.repository.BookingRepository;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class BookingService {

    @Inject
    BookingRepository bookingRepository;

    public Uni<List<Booking>> getAllReservations() {
        return bookingRepository.listAll();
    }

    public Uni<Booking> getReservationById(UUID id) {
        return bookingRepository.findById(id);
    }
    public Uni<Booking> createReservation(Booking reservation) {
        return Panache.withTransaction(() ->
                bookingRepository.persist(reservation)
        ).replaceWith(reservation);
    }
    public Uni<Booking> updateReservation(UUID id, Booking reservationDetails) {
        return bookingRepository.findById(id)
                .onItem().ifNull().failWith(new Exception("Booking not found"))
                .invoke(reservation -> {
                    reservation.roomId = reservationDetails.roomId;
                    reservation.userId = reservationDetails.userId;
                    reservation.transportId = reservationDetails.transportId;
                    reservation.flightId = reservationDetails.flightId;
                    reservation.totalAmount = reservationDetails.totalAmount;
                    reservation.status = reservationDetails.status;
                }).call(reservation -> bookingRepository.persist(reservation));

    }
    public Uni<Boolean> deleteReservation(UUID id) {
        return bookingRepository.delete("id", id)
                .map(count -> count > 0);
    }
}

