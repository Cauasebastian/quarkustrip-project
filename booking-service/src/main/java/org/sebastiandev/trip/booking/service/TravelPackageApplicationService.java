package org.sebastiandev.trip.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Timestamp;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.sebastiandev.trip.booking.domain.BookingItemType;
import org.sebastiandev.trip.booking.domain.TravelPackage;
import org.sebastiandev.trip.booking.domain.TravelPackageItem;
import org.sebastiandev.trip.booking.repository.TravelPackageRepository;
import org.sebastiandev.trip.contracts.grpc.BookingItemRequest;
import org.sebastiandev.trip.contracts.grpc.CreateTravelPackageRequest;
import org.sebastiandev.trip.contracts.grpc.FlightItemRequest;
import org.sebastiandev.trip.contracts.grpc.HotelItemRequest;
import org.sebastiandev.trip.contracts.grpc.LocalDateValue;
import org.sebastiandev.trip.contracts.grpc.Money;
import org.sebastiandev.trip.contracts.grpc.TransportItemRequest;
import org.sebastiandev.trip.contracts.grpc.TravelPackageItemInput;
import org.sebastiandev.trip.contracts.grpc.TravelPackageItemView;
import org.sebastiandev.trip.contracts.grpc.TravelPackageView;

@ApplicationScoped
public class TravelPackageApplicationService {
    @Inject TravelPackageRepository repository;
    @Inject BookingValidator validator;
    @Inject ObjectMapper mapper;

    public Uni<TravelPackage> create(CreateTravelPackageRequest request) {
        validate(request);
        return Panache.withTransaction(() -> repository.persist(entity(request)));
    }

    @WithSession
    public Uni<PackagePage> list(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Uni<List<TravelPackage>> packages = repository.find("active", Sort.descending("createdAt"), true)
                .page(Page.of(safePage, safeSize)).list();
        Uni<Long> total = repository.count("active", true);
        return Uni.combine().all().unis(packages, total).asTuple()
                .map(result -> new PackagePage(result.getItem1(), safePage, safeSize, result.getItem2()));
    }

    public TravelPackageView view(TravelPackage value) {
        return TravelPackageView.newBuilder()
                .setId(value.id.toString())
                .setName(value.name)
                .setDescription(value.description == null ? "" : value.description)
                .setCurrency(value.currency)
                .setCreatedByUserId(value.createdByUserId.toString())
                .addAllItems(value.items.stream().map(this::itemView).toList())
                .setCreatedAt(timestamp(value.createdAt.toInstant()))
                .build();
    }

    private void validate(CreateTravelPackageRequest request) {
        if (request.getName().isBlank() || request.getName().length() > 160) {
            throw new IllegalArgumentException("package name is required and must have at most 160 characters");
        }
        if (request.getItemsCount() == 0) {
            throw new IllegalArgumentException("package must contain at least one item");
        }
        Currency.getInstance(request.getCurrency().toUpperCase());
        validator.parseUuid(request.getCreatedByUserId(), "createdByUserId");
        for (TravelPackageItemInput input : request.getItemsList()) {
            if (input.getItem().getItemCase() == BookingItemRequest.ItemCase.ITEM_NOT_SET) {
                throw new IllegalArgumentException("every package item must have a type");
            }
            if (input.getLabel().isBlank()) {
                throw new IllegalArgumentException("package item label is required");
            }
            if (!request.getCurrency().equalsIgnoreCase(input.getDisplayPrice().getCurrency())
                    || input.getDisplayPrice().getAmountMinor() < 0) {
                throw new IllegalArgumentException("package items must use the package currency");
            }
        }
    }

    private TravelPackage entity(CreateTravelPackageRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TravelPackage value = new TravelPackage();
        value.id = UUID.randomUUID();
        value.name = request.getName().trim();
        value.description = request.getDescription().trim();
        value.currency = request.getCurrency().toUpperCase();
        value.createdByUserId = UUID.fromString(request.getCreatedByUserId());
        value.active = true;
        value.createdAt = now;
        value.updatedAt = now;
        for (TravelPackageItemInput input : request.getItemsList()) {
            TravelPackageItem item = new TravelPackageItem();
            item.id = UUID.randomUUID();
            item.travelPackage = value;
            item.label = input.getLabel().trim();
            item.detail = input.getDetail().trim();
            item.currency = value.currency;
            item.amountMinor = input.getDisplayPrice().getAmountMinor();
            item.requestData = requestData(input.getItem(), item);
            value.items.add(item);
        }
        return value;
    }

    private String requestData(BookingItemRequest request, TravelPackageItem item) {
        Map<String, String> attributes = new HashMap<>();
        switch (request.getItemCase()) {
            case FLIGHT -> {
                item.type = BookingItemType.FLIGHT;
                item.resourceId = validator.parseUuid(request.getFlight().getFlightId(), "flightId");
                attributes.put("seatNumber", request.getFlight().getSeatNumber());
            }
            case HOTEL -> {
                item.type = BookingItemType.HOTEL;
                item.resourceId = validator.parseUuid(request.getHotel().getRoomId(), "roomId");
                attributes.put("checkIn", date(request.getHotel().getCheckIn()).toString());
                attributes.put("checkOut", date(request.getHotel().getCheckOut()).toString());
            }
            case TRANSPORT -> {
                item.type = BookingItemType.TRANSPORT;
                item.resourceId = validator.parseUuid(request.getTransport().getTransportId(), "transportId");
                attributes.put("startsAt", instant(request.getTransport().getStartsAt()).toString());
                attributes.put("endsAt", instant(request.getTransport().getEndsAt()).toString());
            }
            default -> throw new IllegalArgumentException("unsupported package item");
        }
        try {
            return mapper.writeValueAsString(attributes);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize package item", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private TravelPackageItemView itemView(TravelPackageItem value) {
        Map<String, String> attributes;
        try {
            attributes = mapper.readValue(value.requestData, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not read package item", exception);
        }
        BookingItemRequest request = switch (value.type) {
            case FLIGHT -> BookingItemRequest.newBuilder().setFlight(FlightItemRequest.newBuilder()
                    .setFlightId(value.resourceId.toString())
                    .setSeatNumber(attributes.get("seatNumber"))).build();
            case HOTEL -> BookingItemRequest.newBuilder().setHotel(HotelItemRequest.newBuilder()
                    .setRoomId(value.resourceId.toString())
                    .setCheckIn(date(LocalDate.parse(attributes.get("checkIn"))))
                    .setCheckOut(date(LocalDate.parse(attributes.get("checkOut"))))).build();
            case TRANSPORT -> BookingItemRequest.newBuilder().setTransport(TransportItemRequest.newBuilder()
                    .setTransportId(value.resourceId.toString())
                    .setStartsAt(timestamp(Instant.parse(attributes.get("startsAt"))))
                    .setEndsAt(timestamp(Instant.parse(attributes.get("endsAt"))))).build();
        };
        return TravelPackageItemView.newBuilder()
                .setId(value.id.toString())
                .setItem(request)
                .setType(org.sebastiandev.trip.contracts.grpc.BookingItemType.valueOf(value.type.name()))
                .setResourceId(value.resourceId.toString())
                .setDisplayPrice(Money.newBuilder()
                        .setCurrency(value.currency).setAmountMinor(value.amountMinor))
                .setLabel(value.label)
                .setDetail(value.detail == null ? "" : value.detail)
                .build();
    }

    private LocalDate date(LocalDateValue value) {
        return LocalDate.of(value.getYear(), value.getMonth(), value.getDay());
    }

    private LocalDateValue date(LocalDate value) {
        return LocalDateValue.newBuilder().setYear(value.getYear())
                .setMonth(value.getMonthValue()).setDay(value.getDayOfMonth()).build();
    }

    private Instant instant(Timestamp value) {
        return Instant.ofEpochSecond(value.getSeconds(), value.getNanos());
    }

    private Timestamp timestamp(Instant value) {
        return Timestamp.newBuilder().setSeconds(value.getEpochSecond()).setNanos(value.getNano()).build();
    }

    public record PackagePage(List<TravelPackage> packages, int page, int size, long totalElements) {
    }
}
