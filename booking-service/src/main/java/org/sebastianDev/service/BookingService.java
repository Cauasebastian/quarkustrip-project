package org.sebastianDev.service;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.sebastianDev.grpc.*;
import org.sebastianDev.model.Booking;
import org.jboss.logging.Logger;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class BookingService {

    private static final Logger LOG = Logger.getLogger(BookingService.class);

    @GrpcClient("availabilityService")
    MutinyAvailabilityServiceGrpc.MutinyAvailabilityServiceStub availabilityClient;

    public Uni<Booking> createReservation(Booking reservation) {
        CheckRoomAvailabilityRequest request = CheckRoomAvailabilityRequest.newBuilder()
                .setRoomId(reservation.roomId.toString())
                .setCheckInDate(convertToTimestamp(reservation.checkInDate))
                .setCheckOutDate(convertToTimestamp(reservation.checkOutDate))
                .build();

        return availabilityClient.checkRoomAvailability(request)
                .onItem().transformToUni(response -> {
                    if (!response.getIsAvailable()) {
                        LOG.errorf("Quarto não disponível: %s", response.getMessage());
                        return Uni.createFrom().failure(new RuntimeException(response.getMessage()));
                    }
                    return persistReservation(reservation);
                })
                .onFailure().invoke(e -> LOG.error("Falha ao criar reserva", e));
    }

    private Uni<Booking> persistReservation(Booking reservation) {
        return Panache.withTransaction(() -> reservation.persist())
                .onItem().invoke(() -> LOG.info("Reserva criada com sucesso"))
                .onItem().transform(ignore -> reservation);
    }

    private com.google.protobuf.Timestamp convertToTimestamp(java.time.LocalDate date) {
        return com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(date.atStartOfDay(ZoneOffset.UTC).toEpochSecond())
                .build();
    }

    public Uni<List<Booking>> getAllReservations() {
        return Booking.listAll();
    }

    public Uni<Booking> getReservationById(UUID id) {
        return Booking.findById(id);
    }

    public Uni<Booking> updateReservation(UUID id, Booking updatedReservation) {
        return Panache.withTransaction(() -> Booking.findById(id)
                .onItem().ifNotNull().transformToUni(reservation -> {
                    reservation.userId = updatedReservation.userId;
                    reservation.roomId = updatedReservation.roomId;
                    reservation.checkInDate = updatedReservation.checkInDate;
                    reservation.checkOutDate = updatedReservation.checkOutDate;
                    reservation.transportId = updatedReservation.transportId;
                    reservation.flightId = updatedReservation.flightId;
                    reservation.totalAmount = updatedReservation.totalAmount;
                    reservation.status = updatedReservation.status;
                    return reservation.persist();
                })
                .onItem().ifNull().failWith(new RuntimeException("Booking not found")));
    }

    public Uni<Void> deleteReservation(UUID id) {
        return Panache.withTransaction(() -> Booking.deleteById(id))
                .replaceWithVoid();
    }
}
