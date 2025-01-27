package org.sebastianDev.service;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.sebastianDev.grpc.*;
import org.sebastianDev.model.Booking;
import org.jboss.logging.Logger;

import java.time.ZoneOffset;

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
}