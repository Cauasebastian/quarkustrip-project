package org.sebastianDev.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.sebastianDev.repository.HotelReservationRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@ApplicationScoped
public class RoomAvailabilityService {

    @Inject
    HotelReservationRepository reservationRepository;

    @WithSession
    public Uni<Boolean> isRoomAvailable(UUID roomId, OffsetDateTime checkInDate, OffsetDateTime checkOutDate) {
        // Convertendo de OffsetDateTime para LocalDate
        LocalDate checkInLocalDate = checkInDate.toLocalDate();
        LocalDate checkOutLocalDate = checkOutDate.toLocalDate();

        // Verifica se há alguma reserva no período solicitado para o quarto
        return reservationRepository
                .find("room.id = ?1 AND checkInDate < ?2 AND checkOutDate > ?3", roomId, checkOutLocalDate, checkInLocalDate)
                .firstResult()
                .map(existingReservation -> existingReservation == null); // Se não encontrar nenhuma reserva, está disponível
    }
}
