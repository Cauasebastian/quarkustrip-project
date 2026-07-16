package org.sebastiandev.trip.flight.grpc;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.sebastiandev.trip.contracts.grpc.CreateFlightRequest;
import org.sebastiandev.trip.contracts.grpc.CreateFlightResponse;
import org.sebastiandev.trip.contracts.grpc.FlightQueryService;
import org.sebastiandev.trip.contracts.grpc.FlightView;
import org.sebastiandev.trip.contracts.grpc.GetFlightRequest;
import org.sebastiandev.trip.contracts.grpc.GetFlightResponse;
import org.sebastiandev.trip.contracts.grpc.Money;
import org.sebastiandev.trip.contracts.grpc.SearchFlightsRequest;
import org.sebastiandev.trip.contracts.grpc.SearchFlightsResponse;
import org.sebastiandev.trip.flight.domain.Flight;
import org.sebastiandev.trip.flight.domain.FlightSeat;
import org.sebastiandev.trip.flight.repository.FlightRepository;
import org.sebastiandev.trip.flight.repository.FlightSeatRepository;

@GrpcService
public class FlightGrpcService implements FlightQueryService {
    @Inject FlightRepository flights;
    @Inject FlightSeatRepository seats;

    @Override
    public Uni<SearchFlightsResponse> searchFlights(SearchFlightsRequest request) {
        return flights.find("upper(origin) = ?1 and upper(destination) = ?2 and departureTime >= ?3",
                        request.getOrigin().toUpperCase(), request.getDestination().toUpperCase(),
                        dateTime(request.getDepartsAfter())).list()
                .chain(list -> mapAll(list).map(views -> SearchFlightsResponse.newBuilder().addAllFlights(views).build()));
    }

    @Override
    public Uni<GetFlightResponse> getFlight(GetFlightRequest request) {
        try {
            return flights.findById(UUID.fromString(request.getFlightId()))
                    .onItem().ifNull().failWith(Status.NOT_FOUND.asRuntimeException())
                    .chain(this::view)
                    .map(value -> GetFlightResponse.newBuilder().setFlight(value).build());
        } catch (IllegalArgumentException exception) {
            return Uni.createFrom().failure(Status.INVALID_ARGUMENT.asRuntimeException());
        }
    }

    @Override
    public Uni<CreateFlightResponse> createFlight(CreateFlightRequest request) {
        if (request.getTotalSeats() < 1 || request.getSeatPrice().getAmountMinor() < 0) {
            return Uni.createFrom().failure(Status.INVALID_ARGUMENT.withDescription("invalid seats or price").asRuntimeException());
        }
        return Panache.withTransaction(() -> {
            Flight flight = new Flight(); flight.id = UUID.randomUUID(); flight.flightNumber = request.getFlightNumber();
            flight.origin = request.getOrigin().toUpperCase(); flight.destination = request.getDestination().toUpperCase();
            flight.departureTime = dateTime(request.getDepartureTime()); flight.arrivalTime = dateTime(request.getArrivalTime());
            flight.seatPriceMinor = request.getSeatPrice().getAmountMinor(); flight.currency = request.getSeatPrice().getCurrency();
            List<FlightSeat> generated = new ArrayList<>();
            for (int i = 1; i <= request.getTotalSeats(); i++) {
                FlightSeat seat = new FlightSeat(); seat.id = UUID.randomUUID(); seat.flight = flight;
                seat.seatNumber = "S" + i; seat.status = FlightSeat.Status.AVAILABLE; generated.add(seat);
            }
            return flights.persist(flight).chain(() -> seats.persist(generated)).chain(() -> view(flight))
                    .map(value -> CreateFlightResponse.newBuilder().setFlight(value).build());
        });
    }

    private Uni<List<FlightView>> mapAll(List<Flight> values) {
        Uni<List<FlightView>> chain = Uni.createFrom().item(new ArrayList<>());
        for (Flight flight : values) chain = chain.chain(result -> view(flight).invoke(result::add).replaceWith(result));
        return chain;
    }

    private Uni<FlightView> view(Flight flight) {
        return seats.find("flight.id = ?1 and status = ?2", flight.id, FlightSeat.Status.AVAILABLE).list()
                .map(available -> FlightView.newBuilder().setId(flight.id.toString())
                        .setFlightNumber(flight.flightNumber).setOrigin(flight.origin).setDestination(flight.destination)
                        .setDepartureTime(timestamp(flight.departureTime)).setArrivalTime(timestamp(flight.arrivalTime))
                        .setSeatPrice(Money.newBuilder().setCurrency(flight.currency).setAmountMinor(flight.seatPriceMinor))
                        .addAllAvailableSeats(available.stream().map(seat -> seat.seatNumber).toList()).build());
    }

    private OffsetDateTime dateTime(com.google.protobuf.Timestamp value) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(value.getSeconds(), value.getNanos()), ZoneOffset.UTC);
    }
    private com.google.protobuf.Timestamp timestamp(OffsetDateTime value) {
        return com.google.protobuf.Timestamp.newBuilder().setSeconds(value.toEpochSecond()).setNanos(value.getNano()).build();
    }
}
