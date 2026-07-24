package org.sebastiandev.trip.gateway.api;

import com.google.protobuf.Timestamp;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
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
import org.sebastiandev.trip.contracts.grpc.BookingItemRequest;
import org.sebastiandev.trip.contracts.grpc.BookingItemView;
import org.sebastiandev.trip.contracts.grpc.BookingView;
import org.sebastiandev.trip.contracts.grpc.CancelBookingRequest;
import org.sebastiandev.trip.contracts.grpc.CreateBookingRequest;
import org.sebastiandev.trip.contracts.grpc.FlightItemRequest;
import org.sebastiandev.trip.contracts.grpc.GetBookingRequest;
import org.sebastiandev.trip.contracts.grpc.HotelItemRequest;
import org.sebastiandev.trip.contracts.grpc.ListUserBookingsRequest;
import org.sebastiandev.trip.contracts.grpc.LocalDateValue;
import org.sebastiandev.trip.contracts.grpc.Money;
import org.sebastiandev.trip.contracts.grpc.TransportItemRequest;
import org.sebastiandev.trip.gateway.security.CurrentUser;
import org.sebastiandev.trip.gateway.observability.BookingObservabilityService;
import org.sebastiandev.trip.gateway.service.BookingGatewayService;

@Path("/api/v1/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class BookingResource {
    @Inject BookingGatewayService booking;
    @Inject CurrentUser user;
    @Inject BookingObservabilityService observability;

    @POST
    public Uni<Response> create(
            @HeaderParam("Idempotency-Key") String key,
            @Valid BookingApiModels.CreateBooking body,
            @Context UriInfo uri) {
        if (key == null || key.isBlank()) throw new BadRequestException("Idempotency-Key is required");
        if (body == null) throw new BadRequestException("request body is required");
        CreateBookingRequest.Builder request = CreateBookingRequest.newBuilder()
                .setUserId(user.id().toString())
                .setCreatedByUserId(user.id().toString())
                .setIdempotencyKey(key)
                .setCurrency(body.currency().toUpperCase())
                .setPaymentMethodRef(body.paymentMethodRef());
        body.items().forEach(item -> request.addItems(item(item)));
        return booking.create(request.build()).map(result -> {
            URI location = uri.getAbsolutePathBuilder().path(result.getBookingId()).build();
            var response = new BookingApiModels.BookingCreated(
                    result.getBookingId(), result.getStatus().name(), location.toString());
            return Response.accepted(response).location(location).build();
        });
    }

    @GET
    public Uni<BookingApiModels.BookingPage> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        if (page < 0) throw new BadRequestException("page must be zero or greater");
        if (size < 1 || size > 100) throw new BadRequestException("size must be between 1 and 100");
        return booking.list(ListUserBookingsRequest.newBuilder()
                        .setUserId(user.id().toString()).setPage(page).setSize(size).build())
                .map(result -> new BookingApiModels.BookingPage(
                        result.getBookingsList().stream().map(this::view).toList(),
                        result.getPage(), result.getSize(), result.getTotalElements(),
                        result.getTotalElements() == 0 ? 0
                                : (int) Math.ceil((double) result.getTotalElements() / result.getSize())));
    }

    @GET
    @Path("/{id}")
    public Uni<BookingApiModels.BookingSummary> get(@PathParam("id") String id) {
        return booking.get(GetBookingRequest.newBuilder()
                        .setBookingId(id).setRequesterUserId(user.id().toString()).setAdmin(user.admin()).build())
                .map(result -> view(result.getBooking()));
    }

    @GET
    @Path("/{id}/observability")
    public Uni<BookingObservabilityModels.Summary> observability(@PathParam("id") String id) {
        return observability.get(id, user.id(), user.admin());
    }

    @POST
    @Path("/{id}/cancel")
    public Uni<Response> cancel(@PathParam("id") String id, BookingApiModels.Cancel body) {
        String reason = body == null || body.reason() == null || body.reason().isBlank()
                ? "USER_CANCELLED" : body.reason();
        return booking.cancel(CancelBookingRequest.newBuilder()
                        .setBookingId(id).setRequesterUserId(user.id().toString()).setAdmin(user.admin())
                        .setReason(reason).build())
                .map(result -> Response.accepted(new BookingApiModels.BookingCancelled(
                        result.getBookingId(), result.getStatus().name())).build());
    }

    private BookingItemRequest item(BookingApiModels.Item value) {
        BookingItemRequest.Builder item = BookingItemRequest.newBuilder();
        switch (value.type().toUpperCase()) {
            case "FLIGHT" -> {
                if (value.seatNumber() == null || value.seatNumber().isBlank()) {
                    throw new BadRequestException("seatNumber is required for FLIGHT");
                }
                item.setFlight(FlightItemRequest.newBuilder()
                        .setFlightId(value.resourceId()).setSeatNumber(value.seatNumber()));
            }
            case "HOTEL" -> {
                if (value.checkIn() == null || value.checkOut() == null
                        || !value.checkOut().isAfter(value.checkIn())) {
                    throw new BadRequestException("a valid [checkIn, checkOut) interval is required for HOTEL");
                }
                item.setHotel(HotelItemRequest.newBuilder().setRoomId(value.resourceId())
                        .setCheckIn(date(value.checkIn())).setCheckOut(date(value.checkOut())));
            }
            case "TRANSPORT" -> {
                if (value.startsAt() == null || value.endsAt() == null
                        || !value.endsAt().isAfter(value.startsAt())) {
                    throw new BadRequestException("a valid [startsAt, endsAt) interval is required for TRANSPORT");
                }
                item.setTransport(TransportItemRequest.newBuilder().setTransportId(value.resourceId())
                        .setStartsAt(time(value.startsAt())).setEndsAt(time(value.endsAt())));
            }
            default -> throw new BadRequestException("unsupported item type");
        }
        return item.build();
    }

    private BookingApiModels.BookingSummary view(BookingView value) {
        return new BookingApiModels.BookingSummary(
                value.getId(), value.getUserId(), value.getCreatedByUserId(),
                value.getStatus().name(), money(value.getTotal()),
                value.getItemsList().stream().map(this::itemView).toList(), value.getFailureCode(),
                timestamp(value.getCreatedAt()), timestamp(value.getUpdatedAt()));
    }

    private BookingApiModels.BookingItem itemView(BookingItemView value) {
        return new BookingApiModels.BookingItem(
                value.getId(), value.getType().name(), value.getResourceId(), value.getStatus().name(),
                value.getExternalReservationId(), money(value.getPrice()), value.getFailureReason());
    }

    private MoneyApiModel money(Money value) {
        return new MoneyApiModel(value.getCurrency(), value.getAmountMinor());
    }

    private LocalDateValue date(LocalDate value) {
        return LocalDateValue.newBuilder().setYear(value.getYear())
                .setMonth(value.getMonthValue()).setDay(value.getDayOfMonth()).build();
    }

    private Timestamp time(OffsetDateTime value) {
        Instant instant = value.toInstant();
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    private OffsetDateTime timestamp(Timestamp value) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(value.getSeconds(), value.getNanos()), ZoneOffset.UTC);
    }
}
