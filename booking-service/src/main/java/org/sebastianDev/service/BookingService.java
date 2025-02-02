package org.sebastianDev.service;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.sebastianDev.CancelReservationRequest;
import org.sebastianDev.CancelReservationResponse;
import org.sebastianDev.CheckAvailabilityRequest;
import org.sebastianDev.MutinyAvailabilityServiceGrpc;
import org.jboss.logging.Logger;
import org.sebastianDev.model.Booking;
import org.sebastianDev.repository.BookingRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class BookingService {

    private static final Logger LOG = Logger.getLogger(BookingService.class);

    @GrpcClient("availabilityService")
    MutinyAvailabilityServiceGrpc.MutinyAvailabilityServiceStub availabilityClient;

    @Inject
    BookingRepository bookingRepository;

    /**
     * Cria uma reserva de hotel por meio de gRPC e persiste no banco de dados se a reserva for bem-sucedida.
     * @param booking
     * @return Uni<Booking>
     */
    @WithTransaction
    public Uni<Booking> createBooking(Booking booking) {
        if (booking.checkInDate == null || booking.checkOutDate == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Check-in date and check-out date must not be null"));
        }
        LOG.infof("Criando booking: %s", booking);
        // Gerar o ID
        if (booking.id == null) {
            booking.id = UUID.randomUUID(); //  Garante que o ID está definido
        }

        CheckAvailabilityRequest request = CheckAvailabilityRequest.newBuilder()
                .setBookingId(booking.id.toString())
                .setUserId(booking.userId.toString())
                .setRoomId(booking.roomId.toString())
                .setCheckInDate(convertToTimestamp(booking.checkInDate))
                .setCheckOutDate(convertToTimestamp(booking.checkOutDate))
                .build();

        LOG.infof("Verificando disponibilidade: %s", request);
        return availabilityClient.checkAvailabilityAndOccupy(request)
                .onItem().transformToUni(response -> {
                    if (!response.getIsAvailable()) {
                        return Uni.createFrom().failure(new RuntimeException("HotelService: " + response.getMessage()));
                    }
                    LOG.infof("Quarto disponível. Persistindo booking: %s", booking);
                    return persistBooking(booking)
                            .onFailure().call(th -> {
                                LOG.errorf("Persist failed, canceling reservation: %s", th.getMessage());
                                return cancelReservationViaGrpc(booking.roomId, booking.id)
                                        .onFailure().invoke(e -> LOG.errorf("Falha no cancelamento gRPC: %s", e.getMessage()))
                                        .replaceWith(Uni.createFrom().failure(th))
                                        .onItem().transformToUni(ignored -> Uni.createFrom().failure(th));
                            });
                })
                .onItem().invoke(persistedBooking ->
                        LOG.infof("Booking criado com sucesso. ID=%s", persistedBooking.id))
                .onFailure().invoke(th ->
                        LOG.errorf("Falha ao criar booking: %s", th.getMessage()));
    }

    /**
     * Cancela uma reserva por meio de gRPC.
     * @param roomId
     * @param bookingId
     * @return Uni<CancelReservationResponse>
     */
    private Uni<CancelReservationResponse> cancelReservationViaGrpc(UUID roomId, UUID bookingId) {
        CancelReservationRequest request = CancelReservationRequest.newBuilder()
                .setRoomId(roomId.toString())
                .setBookingId(bookingId.toString())
                .build();

        return availabilityClient.cancelReservation(request)
                .onFailure().invoke(e ->
                        LOG.error("Failed to cancel reservation via gRPC", e));
    }

    /**
     * Persiste uma reserva no banco de dados.
     * @param booking
     * @return Uni<Booking>
     */
    private Uni<Booking> persistBooking(Booking booking) {
        if (booking.isPersistent()) {
            return Uni.createFrom().item(booking);
        }
        return Panache.withTransaction(() -> booking.persist())
                .replaceWith(booking);
    }

    /**
     * Converte um objeto LocalDate para Timestamp.
     * @param date
     * @return Timestamp
     */
    private com.google.protobuf.Timestamp convertToTimestamp(java.time.LocalDate date) {
        if (date == null) {
            LOG.error("Data fornecida é nula!");
            throw new IllegalArgumentException("Data fornecida é nula!");
        }
        return com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(date.atStartOfDay(ZoneOffset.UTC).toEpochSecond())
                .build();
    }

    public Uni<List<Booking>> getAllReservations() {
        return bookingRepository.listAll();
    }

    public Uni<Booking> getReservationById(UUID id) {
        return bookingRepository.findById(id);
    }

    public Uni<Booking> updateReservation(UUID id, Booking updatedReservation) {
        return Panache.withTransaction(() -> bookingRepository.findById(id)
                .onItem().ifNotNull().transformToUni(existingBooking -> {
                    existingBooking.updateFrom(updatedReservation);
                    return bookingRepository.persist(existingBooking);
                })
                .onItem().ifNull().failWith(new RuntimeException("Reserva não encontrada com ID: " + id))
        );
    }

    /**
     * Deleta uma reserva de hotel por meio de gRPC e deleta o booking do banco de dados.
     * @param id
     * @return Uni<Void>
     */
    public Uni<Void> deleteReservation(UUID id) {
        return Panache.withTransaction(() ->
                        bookingRepository.findById(id)
                                .onItem().ifNotNull().call(booking ->
                                        bookingRepository.delete(booking)
                                                .onItem().call(() ->
                                                        cancelReservationViaGrpc(booking.roomId, booking.id)
                                                )
                                )
                                .onItem().ifNull().failWith(new RuntimeException("Booking not found"))
                )
                .replaceWithVoid()
                .onFailure().invoke(th ->
                        LOG.error("Failed to delete reservation", th));
    }
}