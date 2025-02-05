package org.sebastianDev.service;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import org.sebastianDev.dto.FlightReservationSummaryDTO;
import org.sebastianDev.dto.ReservationRequest;
import org.sebastianDev.model.FlightReservation;
import org.sebastianDev.model.FlightSeat;
import org.sebastianDev.repository.FlightReservationRepository;
import org.sebastianDev.repository.FlightSeatRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class ReservationService {

    @Inject
    FlightSeatRepository flightSeatRepository;

    @Inject
    FlightReservationRepository reservationRepository;

    public Uni<FlightReservation> createReservation(ReservationRequest request) {
        // Inicia uma transação
        return Panache.withTransaction(() ->
                // Busca o assento específico que queremos reservar
                flightSeatRepository.find("flight.id = ?1 and seatNumber = ?2", request.flightId, request.seatNumber)
                        .firstResult()
                        .onItem().ifNotNull().transformToUni(seat -> {
                            // Verifica se o assento está disponível
                            if (seat.status != FlightSeat.SeatStatus.AVAILABLE) {
                                throw new IllegalStateException("Seat not available");
                            }

                            // Marca o assento como reservado
                            seat.status = FlightSeat.SeatStatus.BOOKED;

                            // Persiste a atualização do assento e cria a reserva
                            return flightSeatRepository.persist(seat)
                                    .onItem().transformToUni(updatedSeat -> {
                                        FlightReservation reservation = new FlightReservation();
                                        reservation.seat = updatedSeat;
                                        reservation.userId = request.userId;
                                        reservation.bookingId = request.bookingId;

                                        // Persiste a reserva
                                        return reservationRepository.persist(reservation);
                                    });
                        })
        );
    }

    // Método para obter todas as reservas
    public Uni<List<FlightReservationSummaryDTO>> getAllReservations() {
        return reservationRepository.listAll()
                .map(reservations -> reservations.stream()
                        .map(this::convertToFlightReservationSummaryDTO)
                        .collect(Collectors.toList()));
    }

    // Método para obter uma reserva por ID
    public Uni<FlightReservationSummaryDTO> getReservationById(UUID id) {
        return reservationRepository.findById(id)
                .onItem().transform(reservation -> {
                    if (reservation != null) {
                        return convertToFlightReservationSummaryDTO((FlightReservation) reservation);
                    } else {
                        return null; // Ou lançar uma exceção, se necessário
                    }
                });
    }

    // Método de conversão para DTO
    private FlightReservationSummaryDTO convertToFlightReservationSummaryDTO(FlightReservation reservation) {
        return new FlightReservationSummaryDTO(
                reservation.id,
                reservation.seat.seatNumber,
                reservation.bookingId,
                reservation.userId,
                reservation.status.name(),
                reservation.createdAt,
                reservation.updatedAt
        );
    }
}