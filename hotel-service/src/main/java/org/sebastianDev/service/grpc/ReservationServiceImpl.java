package org.sebastianDev.service.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.sebastianDev.CreateReservationRequest;
import org.sebastianDev.ReservationResponse;
import org.sebastianDev.ReservationService;
import org.sebastianDev.model.HotelReservation;
import org.sebastianDev.model.Room;
import org.sebastianDev.service.HotelReservationService;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

@GrpcService
public class ReservationServiceImpl implements ReservationService {

    private static final Logger LOG = Logger.getLogger(ReservationServiceImpl.class);

    @Inject
    HotelReservationService reservationService;

    @Override
    public Uni<ReservationResponse> createReservation(CreateReservationRequest request) {
        LOG.infof("Received gRPC request: %s", request);

        return Uni.createFrom().item(() -> mapRequestToReservation(request))
                // Encadeamos a chamada ao serviço que persiste a reserva
                .onItem().transformToUni(reservation -> reservationService.createReservation(reservation))
                // Transformamos a reserva persistida na resposta gRPC
                .map(savedReservation -> ReservationResponse.newBuilder()
                        .setBookingId(savedReservation.id.toString())
                        .setStatus(savedReservation.status == null ? "Booked" : savedReservation.status)
                        .build()
                )
                // Em caso de qualquer falha no fluxo, construímos uma resposta de erro
                .onFailure().recoverWithItem(throwable -> {
                    LOG.error("Error during reservation creation", throwable);
                    return ReservationResponse.newBuilder()
                            .setBookingId("")
                            .setStatus("Failed: " + throwable.getMessage())
                            .build();
                });
    }

    /**
     * Converte o CreateReservationRequest em um objeto de domínio (HotelReservation).
     * Essa lógica foi extraída para um método à parte, para manter o código mais limpo.
     */
    private HotelReservation mapRequestToReservation(CreateReservationRequest request) {
        Instant checkInInstant = Instant.ofEpochSecond(
                request.getCheckInDate().getSeconds(),
                request.getCheckInDate().getNanos()
        );
        Instant checkOutInstant = Instant.ofEpochSecond(
                request.getCheckOutDate().getSeconds(),
                request.getCheckOutDate().getNanos()
        );

        HotelReservation reservation = new HotelReservation();
        reservation.bookingId = UUID.fromString(request.getBookingId()); // <-- NOVO!
        reservation.userId = UUID.fromString(request.getUserId());
        reservation.room = new Room();
        reservation.room.id = UUID.fromString(request.getRoomId());
        reservation.checkInDate = checkInInstant.atZone(ZoneId.systemDefault()).toLocalDate();
        reservation.checkOutDate = checkOutInstant.atZone(ZoneId.systemDefault()).toLocalDate();
        reservation.status = "Booked";

        return reservation;
    }
}
