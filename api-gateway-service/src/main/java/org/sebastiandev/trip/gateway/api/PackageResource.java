package org.sebastiandev.trip.gateway.api;

import com.google.protobuf.Timestamp;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.sebastiandev.trip.contracts.grpc.BookingItemRequest;
import org.sebastiandev.trip.contracts.grpc.ListTravelPackagesRequest;
import org.sebastiandev.trip.contracts.grpc.TravelPackageItemView;
import org.sebastiandev.trip.contracts.grpc.TravelPackageView;
import org.sebastiandev.trip.gateway.service.BookingGatewayService;

@Path("/api/v1/packages")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class PackageResource {
    @Inject BookingGatewayService booking;

    @GET
    public Uni<OperatorApiModels.PackagePage> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("invalid page or size");
        }
        return booking.listPackages(ListTravelPackagesRequest.newBuilder()
                        .setPage(page).setSize(size).build())
                .map(result -> new OperatorApiModels.PackagePage(
                        result.getPackagesList().stream().map(PackageResource::view).toList(),
                        result.getPage(), result.getSize(), result.getTotalElements(),
                        result.getTotalElements() == 0 ? 0
                                : (int) Math.ceil((double) result.getTotalElements() / result.getSize())));
    }

    static OperatorApiModels.TravelPackage view(TravelPackageView value) {
        return new OperatorApiModels.TravelPackage(value.getId(), value.getName(),
                value.getDescription(), value.getCurrency(),
                value.getItemsList().stream().map(PackageResource::item).toList(),
                offset(value.getCreatedAt()));
    }

    private static OperatorApiModels.PackageItem item(TravelPackageItemView value) {
        return new OperatorApiModels.PackageItem(value.getId(), item(value.getItem()),
                value.getType().name(), value.getResourceId(),
                new MoneyApiModel(value.getDisplayPrice().getCurrency(),
                        value.getDisplayPrice().getAmountMinor()),
                value.getLabel(), value.getDetail());
    }

    private static BookingApiModels.Item item(BookingItemRequest value) {
        return switch (value.getItemCase()) {
            case FLIGHT -> new BookingApiModels.Item("FLIGHT", value.getFlight().getFlightId(),
                    value.getFlight().getSeatNumber(), null, null, null, null);
            case HOTEL -> new BookingApiModels.Item("HOTEL", value.getHotel().getRoomId(), null,
                    date(value.getHotel().getCheckIn()), date(value.getHotel().getCheckOut()), null, null);
            case TRANSPORT -> new BookingApiModels.Item("TRANSPORT",
                    value.getTransport().getTransportId(), null, null, null,
                    offset(value.getTransport().getStartsAt()), offset(value.getTransport().getEndsAt()));
            default -> throw new IllegalStateException("package item has no type");
        };
    }

    private static LocalDate date(org.sebastiandev.trip.contracts.grpc.LocalDateValue value) {
        return LocalDate.of(value.getYear(), value.getMonth(), value.getDay());
    }

    private static OffsetDateTime offset(Timestamp value) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(value.getSeconds(), value.getNanos()),
                ZoneOffset.UTC);
    }
}
