package org.sebastiandev.trip.gateway.api;

import com.google.protobuf.Timestamp;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.sebastiandev.trip.contracts.grpc.BookingItemRequest;
import org.sebastiandev.trip.contracts.grpc.BookingItemView;
import org.sebastiandev.trip.contracts.grpc.BookingView;
import org.sebastiandev.trip.contracts.grpc.CreateBookingRequest;
import org.sebastiandev.trip.contracts.grpc.CreateTravelPackageRequest;
import org.sebastiandev.trip.contracts.grpc.FlightItemRequest;
import org.sebastiandev.trip.contracts.grpc.GetUserProfileRequest;
import org.sebastiandev.trip.contracts.grpc.HotelItemRequest;
import org.sebastiandev.trip.contracts.grpc.ListCreatedBookingsRequest;
import org.sebastiandev.trip.contracts.grpc.LocalDateValue;
import org.sebastiandev.trip.contracts.grpc.Money;
import org.sebastiandev.trip.contracts.grpc.SearchUserProfilesRequest;
import org.sebastiandev.trip.contracts.grpc.TransportItemRequest;
import org.sebastiandev.trip.contracts.grpc.TravelPackageItemInput;
import org.sebastiandev.trip.gateway.security.CurrentUser;
import org.sebastiandev.trip.gateway.service.BookingGatewayService;
import org.sebastiandev.trip.gateway.service.UserGatewayService;

@Path("/api/v1/operator")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"OPERATOR", "ADMIN"})
public class OperatorResource {
    @Inject BookingGatewayService booking;
    @Inject UserGatewayService users;
    @Inject CurrentUser currentUser;

    @GET
    @Path("/users")
    public Uni<UserApiModels.ProfilePage> users(
            @QueryParam("q") String query,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        if (query == null || query.trim().length() < 2) {
            throw new BadRequestException("q must contain at least two characters");
        }
        if (page < 0 || size < 1 || size > 50) {
            throw new BadRequestException("invalid page or size");
        }
        return users.search(SearchUserProfilesRequest.newBuilder()
                        .setQuery(query.trim()).setPage(page).setSize(size).build())
                .map(result -> new UserApiModels.ProfilePage(
                        result.getProfilesList().stream().map(value -> new UserApiModels.Profile(
                                value.getId(), value.getSubject(), value.getEmail(),
                                value.getFirstName(), value.getLastName(), value.getPreferencesJson())).toList(),
                        result.getPage(), result.getSize(), result.getTotalElements()));
    }

    @GET
    @Path("/bookings")
    public Uni<BookingApiModels.BookingPage> bookings(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("invalid page or size");
        }
        return booking.listCreated(ListCreatedBookingsRequest.newBuilder()
                        .setCreatedByUserId(currentUser.id().toString()).setPage(page).setSize(size).build())
                .map(result -> new BookingApiModels.BookingPage(
                        result.getBookingsList().stream().map(this::view).toList(),
                        result.getPage(), result.getSize(), result.getTotalElements(),
                        pages(result.getTotalElements(), result.getSize())));
    }

    @POST
    @Path("/bookings")
    public Uni<Response> createBooking(
            @HeaderParam("Idempotency-Key") String key,
            @Valid OperatorApiModels.CreateBooking body,
            @Context UriInfo uri) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Idempotency-Key is required");
        }
        try {
            UUID.fromString(body.userId());
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("userId must be a UUID");
        }
        CreateBookingRequest.Builder request = CreateBookingRequest.newBuilder()
                .setUserId(body.userId())
                .setCreatedByUserId(currentUser.id().toString())
                .setIdempotencyKey(key)
                .setCurrency(body.currency().toUpperCase())
                .setPaymentMethodRef(body.paymentMethodRef());
        body.items().forEach(value -> request.addItems(item(value)));
        return users.get(GetUserProfileRequest.newBuilder().setId(body.userId()).build())
                .chain(ignored -> booking.create(request.build()))
                .map(result -> {
                    URI location = uri.getBaseUriBuilder().path("api/v1/bookings")
                            .path(result.getBookingId()).build();
                    return Response.accepted(new BookingApiModels.BookingCreated(
                            result.getBookingId(), result.getStatus().name(), location.toString()))
                            .location(location).build();
                });
    }

    @POST
    @Path("/packages")
    public Uni<OperatorApiModels.TravelPackage> createPackage(
            @Valid OperatorApiModels.CreatePackage body) {
        CreateTravelPackageRequest.Builder request = CreateTravelPackageRequest.newBuilder()
                .setName(body.name())
                .setDescription(body.description() == null ? "" : body.description())
                .setCurrency(body.currency().toUpperCase())
                .setCreatedByUserId(currentUser.id().toString());
        body.items().forEach(value -> request.addItems(TravelPackageItemInput.newBuilder()
                .setItem(item(value.item()))
                .setDisplayPrice(money(value.displayPrice()))
                .setLabel(value.label())
                .setDetail(value.detail() == null ? "" : value.detail())));
        return booking.createPackage(request.build()).map(result -> PackageResource.view(result.getPackage()));
    }

    private BookingApiModels.BookingSummary view(BookingView value) {
        return new BookingApiModels.BookingSummary(value.getId(), value.getUserId(),
                value.getCreatedByUserId(), value.getStatus().name(), money(value.getTotal()),
                value.getItemsList().stream().map(this::itemView).toList(), value.getFailureCode(),
                offset(value.getCreatedAt()), offset(value.getUpdatedAt()));
    }

    private BookingApiModels.BookingItem itemView(BookingItemView value) {
        return new BookingApiModels.BookingItem(value.getId(), value.getType().name(),
                value.getResourceId(), value.getStatus().name(), value.getExternalReservationId(),
                money(value.getPrice()), value.getFailureReason());
    }

    private BookingItemRequest item(BookingApiModels.Item value) {
        BookingItemRequest.Builder request = BookingItemRequest.newBuilder();
        switch (value.type().toUpperCase()) {
            case "FLIGHT" -> {
                if (value.seatNumber() == null || value.seatNumber().isBlank()) {
                    throw new BadRequestException("seatNumber is required for FLIGHT");
                }
                request.setFlight(FlightItemRequest.newBuilder()
                        .setFlightId(value.resourceId()).setSeatNumber(value.seatNumber()));
            }
            case "HOTEL" -> {
                if (value.checkIn() == null || value.checkOut() == null
                        || !value.checkOut().isAfter(value.checkIn())) {
                    throw new BadRequestException("valid hotel dates are required");
                }
                request.setHotel(HotelItemRequest.newBuilder().setRoomId(value.resourceId())
                        .setCheckIn(date(value.checkIn())).setCheckOut(date(value.checkOut())));
            }
            case "TRANSPORT" -> {
                if (value.startsAt() == null || value.endsAt() == null
                        || !value.endsAt().isAfter(value.startsAt())) {
                    throw new BadRequestException("valid transport dates are required");
                }
                request.setTransport(TransportItemRequest.newBuilder()
                        .setTransportId(value.resourceId())
                        .setStartsAt(timestamp(value.startsAt())).setEndsAt(timestamp(value.endsAt())));
            }
            default -> throw new BadRequestException("unsupported item type");
        }
        return request.build();
    }

    private Money money(MoneyApiModel value) {
        MoneyApiModel normalized = value.normalized();
        return Money.newBuilder().setCurrency(normalized.currency())
                .setAmountMinor(normalized.amountMinor()).build();
    }

    private MoneyApiModel money(Money value) {
        return new MoneyApiModel(value.getCurrency(), value.getAmountMinor());
    }

    private LocalDateValue date(LocalDate value) {
        return LocalDateValue.newBuilder().setYear(value.getYear())
                .setMonth(value.getMonthValue()).setDay(value.getDayOfMonth()).build();
    }

    private Timestamp timestamp(OffsetDateTime value) {
        Instant instant = value.toInstant();
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    private OffsetDateTime offset(Timestamp value) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(value.getSeconds(), value.getNanos()),
                ZoneOffset.UTC);
    }

    private int pages(long total, int size) {
        return total == 0 ? 0 : (int) Math.ceil((double) total / size);
    }
}
