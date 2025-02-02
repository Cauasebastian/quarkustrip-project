package org.sebastianDev.service.grpc;

import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.sebastianDev.AvailabilityService;
import org.sebastianDev.CheckAvailabilityRequest;
import org.sebastianDev.CheckAvailabilityResponse;
import org.sebastianDev.repository.RoomRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@GrpcService
public class AvailabilityGrpcService implements AvailabilityService {

    private static final Logger LOG = Logger.getLogger(AvailabilityGrpcService.class);

    @Inject
    RoomRepository roomRepository;

    @Override
    public Uni<CheckAvailabilityResponse> checkAvailabilityAndOccupy(CheckAvailabilityRequest request) {
        UUID bookingId = UUID.fromString(request.getBookingId());
        UUID userId = UUID.fromString(request.getUserId());
        UUID roomId = UUID.fromString(request.getRoomId());

        OffsetDateTime checkInDate = convertToOffsetDateTime(request.getCheckInDate());
        OffsetDateTime checkOutDate = convertToOffsetDateTime(request.getCheckOutDate());

        LOG.infof("Recebido request: bookingId=%s, userId=%s, roomId=%s, checkIn=%s, checkOut=%s",
                bookingId, userId, roomId, checkInDate, checkOutDate);

        return Panache.withTransaction(() -> roomRepository.findById(roomId)
                .onItem().ifNull().failWith(new RuntimeException("Quarto não encontrado"))
                .onItem().transformToUni(room -> {
                    if (room.isAvailableForPeriod(checkInDate, checkOutDate)) {
                        room.setAvailable(false);
                        LOG.infof("Quarto disponível. Reservando...");
                        return roomRepository.persist(room)
                                .onItem().transformToUni(v -> {
                                    return Uni.createFrom().item(
                                            CheckAvailabilityResponse.newBuilder()
                                                    .setIsAvailable(true)
                                                    .setMessage("Quarto disponível e reservado com sucesso.")
                                                    .build()
                                    );
                                });
                    } else {
                        return Uni.createFrom().item(
                                CheckAvailabilityResponse.newBuilder()
                                        .setIsAvailable(false)
                                        .setMessage("Quarto não disponível para as datas solicitadas.")
                                        .build()
                        );
                    }
                })
        );
    }

    private OffsetDateTime convertToOffsetDateTime(com.google.protobuf.Timestamp timestamp) {
        if (timestamp == null) {
            LOG.error("Timestamp fornecido é nulo!");
            throw new IllegalArgumentException("Timestamp fornecido é nulo!");
        }
        return OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()), java.time.ZoneOffset.UTC);
    }
}