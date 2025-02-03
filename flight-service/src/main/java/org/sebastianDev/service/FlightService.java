package org.sebastianDev.service;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.sebastianDev.dto.FlightDTO;
import org.sebastianDev.dto.FlightRequest;
import org.sebastianDev.dto.SeatInfoDTO;
import org.sebastianDev.model.Flight;
import org.sebastianDev.model.FlightSeat;
import org.sebastianDev.repository.FlightRepository;
import org.sebastianDev.repository.FlightSeatRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@ApplicationScoped
public class FlightService {

    @Inject
    FlightRepository flightRepository;

    @Inject
    FlightSeatRepository flightSeatRepository;

    public Uni<Flight> createFlight(FlightRequest request) {
        Flight flight = new Flight();
        flight.flightNumber = request.flightNumber;
        flight.origin = request.origin;
        flight.destination = request.destination;
        flight.departureTime = request.departureTime;
        flight.arrivalTime = request.arrivalTime;
        flight.totalSeats = request.totalSeats;

        return Panache.withTransaction(() -> flightRepository.persist(flight)
                .call(savedFlight -> {
                    List<FlightSeat> seats = IntStream.range(0, request.totalSeats) // Começa do 0
                            .mapToObj(i -> {
                                FlightSeat seat = new FlightSeat();
                                seat.flight = savedFlight;
                                seat.seatNumber = generateSeatNumber(i);
                                return seat;
                            })
                            .toList();
                    return flightSeatRepository.persist(seats);
                })
        ).replaceWith(flight);
    }

    public Uni<List<FlightDTO>> getAllFlights() {
        // Corrigido: Usar JOIN FETCH para carregar assentos e evitar N+1
        return flightRepository.list("FROM Flight f LEFT JOIN FETCH f.seats")
                .map(flights -> flights.stream()
                        .map(this::convertToDTO)
                        .toList());
    }

    public Uni<Flight> getFlightById(UUID id) {
        // Corrigido: Carregar assentos junto com o voo
        return flightRepository.find("FROM Flight f LEFT JOIN FETCH f.seats WHERE f.id = ?1", id)
                .firstResult();
    }
    //getSeatInfoByFlightId
    public Uni<List<SeatInfoDTO>> getFlightSeatsSummary(UUID flightId) {
        return flightSeatRepository.list("flight.id", flightId)
                .map(seats -> seats.stream()
                        .map(seat -> new SeatInfoDTO(
                                seat.seatNumber,
                                seat.status.toString()
                        ))
                        .toList()
                );
    }

    // Assentos agrupados por fileira
    public Uni<Map<String, Map<String, String>>> getFlightSeatsSummaryGrouped(UUID flightId) {
        return flightSeatRepository.list("flight.id", flightId)
                .map(seats -> {
                    Map<String, Map<String, String>> groupedSeats = new LinkedHashMap<>();

                    seats.forEach(seat -> {
                        // Regex melhorada para capturar letras e números
                        Matcher matcher = Pattern.compile("(\\d+)(\\D+)").matcher(seat.seatNumber);
                        if (matcher.find()) {
                            String row = matcher.group(1);
                            String column = matcher.group(2);

                            groupedSeats
                                    .computeIfAbsent(row, k -> new LinkedHashMap<>())
                                    .put(column, seat.status.toString());
                        } else {
                            // Log ou tratamento para formato inválido
                            System.err.println("Formato de assento inválido: " + seat.seatNumber);
                        }
                    });

                    return groupedSeats;
                });
    }


    private FlightDTO convertToDTO(Flight flight) {
        FlightDTO dto = new FlightDTO();
        dto.id = flight.id;
        dto.flightNumber = flight.flightNumber;
        dto.origin = flight.origin;
        dto.destination = flight.destination;
        dto.departureTime = flight.departureTime;
        dto.arrivalTime = flight.arrivalTime;
        dto.totalSeats = flight.totalSeats;
        dto.createdAt = flight.createdAt;
        dto.updatedAt = flight.updatedAt;

        // Calcula assentos disponíveis de forma segura
        if(flight.seats != null) {
            dto.availableSeats = (int) flight.seats.stream()
                    .filter(s -> s.status == FlightSeat.SeatStatus.AVAILABLE)
                    .count();
        }
        return dto;
    }

    private String generateSeatNumber(int index) {
        int row = (index / 6) + 1;
        char column = (char) ('A' + (index % 6));
        return String.format("%d%s", row, column); // Garante formato numérico + letra
    }
}