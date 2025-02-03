package org.sebastianDev.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.sebastianDev.dto.FlightRequest;
import org.sebastianDev.model.Flight;
import org.sebastianDev.model.FlightSeat;
import org.sebastianDev.repository.FlightRepository;
import org.sebastianDev.repository.FlightSeatRepository;

import java.util.List;
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

        return flightRepository.persist(flight)
                .call(savedFlight -> {
                    List<FlightSeat> seats = IntStream.rangeClosed(1, request.totalSeats)
                            .mapToObj(i -> {
                                FlightSeat seat = new FlightSeat();
                                seat.flight = savedFlight;
                                seat.seatNumber = "A" + i;
                                return seat;
                            })
                            .toList();
                    return flightSeatRepository.persist(seats);
                });
    }
    public Uni<List<Flight>> getAllFlights() {
        return flightRepository.listAll();
    }
    public Uni<Flight> getFlightById(Long id) {
        return flightRepository.findById(id);
    }
}