package org.sebastiandev.trip.gateway.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Timestamp;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.sebastiandev.trip.contracts.grpc.CreateFlightRequest;
import org.sebastiandev.trip.contracts.grpc.CreateHotelRequest;
import org.sebastiandev.trip.contracts.grpc.CreateRoomRequest;
import org.sebastiandev.trip.contracts.grpc.CreateTransportRequest;
import org.sebastiandev.trip.contracts.grpc.FlightView;
import org.sebastiandev.trip.contracts.grpc.HotelView;
import org.sebastiandev.trip.contracts.grpc.ListRoomsRequest;
import org.sebastiandev.trip.contracts.grpc.LocalDateValue;
import org.sebastiandev.trip.contracts.grpc.Money;
import org.sebastiandev.trip.contracts.grpc.RoomView;
import org.sebastiandev.trip.contracts.grpc.SearchFlightsRequest;
import org.sebastiandev.trip.contracts.grpc.SearchHotelsRequest;
import org.sebastiandev.trip.contracts.grpc.SearchTransportsRequest;
import org.sebastiandev.trip.contracts.grpc.TransportView;
import org.sebastiandev.trip.gateway.service.CatalogGatewayService;

@Path("/api/v1/catalog")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class CatalogResource {
    @Inject CatalogGatewayService catalog;
    @Inject ObjectMapper objectMapper;

    @GET
    @Path("/flights")
    public Uni<CatalogApiModels.Flights> flights(
            @QueryParam("origin") String origin,
            @QueryParam("destination") String destination,
            @QueryParam("departsAfter") OffsetDateTime after) {
        if (origin == null || !origin.matches("(?i)[a-z]{3}")
                || destination == null || !destination.matches("(?i)[a-z]{3}")) {
            throw new BadRequestException("origin and destination must be three-letter codes");
        }
        OffsetDateTime departure = after == null ? OffsetDateTime.now(ZoneOffset.UTC) : after;
        return catalog.searchFlights(SearchFlightsRequest.newBuilder()
                        .setOrigin(origin.toUpperCase()).setDestination(destination.toUpperCase())
                        .setDepartsAfter(timestamp(departure)).build())
                .map(result -> new CatalogApiModels.Flights(
                        result.getFlightsList().stream().map(this::flight).toList()));
    }

    @POST
    @Path("/flights")
    @RolesAllowed("ADMIN")
    public Uni<CatalogApiModels.Flight> createFlight(@Valid CatalogApiModels.CreateFlight body) {
        if (!body.arrivalTime().isAfter(body.departureTime())) {
            throw new BadRequestException("arrivalTime must be after departureTime");
        }
        return catalog.createFlight(CreateFlightRequest.newBuilder()
                        .setFlightNumber(body.flightNumber()).setOrigin(body.origin().toUpperCase())
                        .setDestination(body.destination().toUpperCase())
                        .setDepartureTime(timestamp(body.departureTime())).setArrivalTime(timestamp(body.arrivalTime()))
                        .setTotalSeats(body.totalSeats()).setSeatPrice(money(body.seatPrice())).build())
                .map(result -> flight(result.getFlight()));
    }

    @GET
    @Path("/hotels")
    public Uni<CatalogApiModels.Hotels> hotels(
            @QueryParam("city") String city,
            @QueryParam("country") String country,
            @QueryParam("checkIn") LocalDate checkIn,
            @QueryParam("checkOut") LocalDate checkOut) {
        requireStay(city, country, checkIn, checkOut);
        return catalog.searchHotels(SearchHotelsRequest.newBuilder().setCity(city)
                        .setCountry(country.toUpperCase()).setCheckIn(date(checkIn)).setCheckOut(date(checkOut)).build())
                .map(result -> new CatalogApiModels.Hotels(
                        result.getHotelsList().stream().map(this::hotel).toList()));
    }

    @GET
    @Path("/hotels/{hotelId}/rooms")
    public Uni<CatalogApiModels.Rooms> rooms(
            @PathParam("hotelId") String hotelId,
            @QueryParam("checkIn") LocalDate checkIn,
            @QueryParam("checkOut") LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new BadRequestException("a valid [checkIn, checkOut) interval is required");
        }
        return catalog.listRooms(ListRoomsRequest.newBuilder().setHotelId(hotelId)
                        .setCheckIn(date(checkIn)).setCheckOut(date(checkOut)).build())
                .map(result -> new CatalogApiModels.Rooms(
                        result.getRoomsList().stream().map(this::room).toList()));
    }

    @POST
    @Path("/hotels")
    @RolesAllowed("ADMIN")
    public Uni<CatalogApiModels.Hotel> createHotel(@Valid CatalogApiModels.CreateHotel body) {
        return catalog.createHotel(CreateHotelRequest.newBuilder().setName(body.name()).setAddress(body.address())
                        .setCity(body.city()).setCountry(body.country().toUpperCase()).setRating(body.rating()).build())
                .map(result -> hotel(result.getHotel()));
    }

    @POST
    @Path("/rooms")
    @RolesAllowed("ADMIN")
    public Uni<CatalogApiModels.Room> createRoom(@Valid CatalogApiModels.CreateRoom body) {
        return catalog.createRoom(CreateRoomRequest.newBuilder().setHotelId(body.hotelId())
                        .setRoomNumber(body.roomNumber()).setRoomType(body.roomType())
                        .setNightlyPrice(money(body.nightlyPrice())).build())
                .map(result -> room(result.getRoom()));
    }

    @GET
    @Path("/transports")
    public Uni<CatalogApiModels.Transports> transports(
            @QueryParam("type") String type,
            @QueryParam("startsAt") OffsetDateTime startsAt,
            @QueryParam("endsAt") OffsetDateTime endsAt) {
        if (type == null || type.isBlank() || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new BadRequestException("type and a valid [startsAt, endsAt) interval are required");
        }
        return catalog.searchTransports(SearchTransportsRequest.newBuilder().setTransportType(type)
                        .setStartsAt(timestamp(startsAt)).setEndsAt(timestamp(endsAt)).build())
                .map(result -> new CatalogApiModels.Transports(
                        result.getTransportsList().stream().map(this::transport).toList()));
    }

    @POST
    @Path("/transports")
    @RolesAllowed("ADMIN")
    public Uni<CatalogApiModels.Transport> createTransport(@Valid CatalogApiModels.CreateTransport body) {
        try {
            objectMapper.readTree(body.vehicleDetailsJson());
        } catch (Exception exception) {
            throw new BadRequestException("vehicleDetailsJson must contain valid JSON");
        }
        return catalog.createTransport(CreateTransportRequest.newBuilder()
                        .setTransportType(body.transportType()).setProviderName(body.providerName())
                        .setVehicleDetailsJson(body.vehicleDetailsJson()).setPrice(money(body.price())).build())
                .map(result -> transport(result.getTransport()));
    }

    private void requireStay(String city, String country, LocalDate checkIn, LocalDate checkOut) {
        if (city == null || city.isBlank() || country == null || !country.matches("(?i)[a-z]{2}")
                || checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new BadRequestException("city, two-letter country and a valid stay interval are required");
        }
    }

    private CatalogApiModels.Flight flight(FlightView value) {
        return new CatalogApiModels.Flight(value.getId(), value.getFlightNumber(), value.getOrigin(),
                value.getDestination(), offset(value.getDepartureTime()), offset(value.getArrivalTime()),
                money(value.getSeatPrice()), value.getAvailableSeatsList());
    }

    private CatalogApiModels.Hotel hotel(HotelView value) {
        return new CatalogApiModels.Hotel(value.getId(), value.getName(), value.getAddress(), value.getCity(),
                value.getCountry(), value.getRating());
    }

    private CatalogApiModels.Room room(RoomView value) {
        return new CatalogApiModels.Room(value.getId(), value.getHotelId(), value.getRoomNumber(),
                value.getRoomType(), money(value.getNightlyPrice()), value.getAvailable());
    }

    private CatalogApiModels.Transport transport(TransportView value) {
        return new CatalogApiModels.Transport(value.getId(), value.getTransportType(), value.getProviderName(),
                value.getVehicleDetailsJson(), money(value.getPrice()), value.getAvailable());
    }

    private Money money(MoneyApiModel value) {
        MoneyApiModel normalized = value.normalized();
        return Money.newBuilder().setCurrency(normalized.currency()).setAmountMinor(normalized.amountMinor()).build();
    }

    private MoneyApiModel money(Money value) {
        return new MoneyApiModel(value.getCurrency(), value.getAmountMinor());
    }

    private Timestamp timestamp(OffsetDateTime value) {
        Instant instant = value.toInstant();
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    private OffsetDateTime offset(Timestamp value) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(value.getSeconds(), value.getNanos()), ZoneOffset.UTC);
    }

    private LocalDateValue date(LocalDate value) {
        return LocalDateValue.newBuilder().setYear(value.getYear())
                .setMonth(value.getMonthValue()).setDay(value.getDayOfMonth()).build();
    }
}
