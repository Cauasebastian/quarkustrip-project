package org.sebastianDev.service.grpc;

import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.sebastianDev.AvailabilityService;
import org.sebastianDev.CheckRoomAvailabilityRequest;
import org.sebastianDev.RoomAvailabilityResponse;
import org.sebastianDev.service.RoomAvailabilityService;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@GrpcService
public class HotelServiceImpl implements AvailabilityService {

    private static final Logger LOG = Logger.getLogger(HotelServiceImpl.class);

    @Inject
    RoomAvailabilityService roomAvailabilityService;


    @Override
    public Uni<RoomAvailabilityResponse> checkRoomAvailability(CheckRoomAvailabilityRequest request) {
        LOG.infof("Received gRPC request: %s", request);
        UUID roomId = UUID.fromString(request.getRoomId());
        OffsetDateTime checkInDate = OffsetDateTime.ofInstant(Instant.ofEpochSecond(request.getCheckInDate().getSeconds(), request.getCheckInDate().getNanos()), ZoneOffset.UTC);
        OffsetDateTime checkOutDate = OffsetDateTime.ofInstant(Instant.ofEpochSecond(request.getCheckOutDate().getSeconds(), request.getCheckOutDate().getNanos()), ZoneOffset.UTC);

        return roomAvailabilityService.isRoomAvailable(roomId, checkInDate, checkOutDate)
                .map(isAvailable -> RoomAvailabilityResponse.newBuilder()
                        .setIsAvailable(isAvailable)
                        .setMessage(isAvailable ? "Room is available" : "Room is not available")
                        .build()
                );
    }
}