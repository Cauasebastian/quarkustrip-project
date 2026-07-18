package org.sebastiandev.trip.notification.grpc;

import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.UUID;
import org.sebastiandev.trip.contracts.grpc.GetNotificationRequest;
import org.sebastiandev.trip.contracts.grpc.GetNotificationResponse;
import org.sebastiandev.trip.contracts.grpc.NotificationQueryService;
import org.sebastiandev.trip.contracts.grpc.NotificationView;
import org.sebastiandev.trip.notification.service.NotificationApplicationService;

@GrpcService
public class NotificationGrpcService implements NotificationQueryService {
    @Inject NotificationApplicationService service;

    @Override
    public Uni<GetNotificationResponse> getNotification(GetNotificationRequest request) {
        try {
            return service.get(UUID.fromString(request.getNotificationId()))
                    .onItem().ifNull().failWith(Status.NOT_FOUND.asRuntimeException())
                    .invoke(notification -> {
                        if (!request.getAdmin() && !notification.userId.toString().equals(request.getUserId())) {
                            throw Status.PERMISSION_DENIED.asRuntimeException();
                        }
                    }).map(notification -> GetNotificationResponse.newBuilder().setNotification(
                            NotificationView.newBuilder()
                                    .setId(notification.id.toString())
                                    .setUserId(notification.userId.toString())
                                    .setType(notification.type)
                                    .setStatus(notification.status)
                                    .setPayloadJson(notification.payloadJson)
                                    .setCreatedAt(Timestamp.newBuilder()
                                            .setSeconds(notification.createdAt.toEpochSecond())
                                            .setNanos(notification.createdAt.getNano()))).build());
        } catch (IllegalArgumentException exception) {
            return Uni.createFrom().failure(Status.INVALID_ARGUMENT.asRuntimeException());
        }
    }
}
