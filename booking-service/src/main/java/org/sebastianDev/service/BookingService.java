package org.sebastianDev.service;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.sebastianDev.grpc.*;
import org.sebastianDev.model.Booking;
import org.jboss.logging.Logger;
import org.sebastianDev.repository.BookingRepository;

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

    public Uni<Booking> createBooking(Booking booking) {
        // Logando as datas para depuração
        LOG.infof("Data de Check-in: %s, Data de Check-out: %s", booking.checkInDate, booking.checkOutDate);

        // Verificação se as datas estão nulas
        if (booking.checkInDate == null || booking.checkOutDate == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Check-in date and check-out date must not be null"));
        }
        // Gere o ID se não existir
        if (booking.id == null) {
            booking.id = UUID.randomUUID(); //  Garante que o ID está definido
        }

        LOG.infof("Criando booking: id=%s, roomId=%s", booking.id, booking.roomId);

        CheckAvailabilityRequest request = CheckAvailabilityRequest.newBuilder()
                .setBookingId(booking.id.toString())
                .setUserId(booking.userId.toString())
                .setRoomId(booking.roomId.toString())
                .setCheckInDate(convertToTimestamp(booking.checkInDate))  // Aqui usamos a verificação com o novo método
                .setCheckOutDate(convertToTimestamp(booking.checkOutDate)) // Aqui usamos a verificação com o novo método
                .build();

        return availabilityClient.checkAvailabilityAndOccupy(request)
                .onItem().transformToUni((CheckAvailabilityResponse response) -> {
                    if (!response.getIsAvailable()) {
                        // Quarto não disponível -> falha
                        return Uni.createFrom().failure(new RuntimeException("HotelService: " + response.getMessage()));
                    }

                    LOG.infof("Quarto disponível (%s). Persistindo booking localmente...", response.getMessage());

                    return persistBooking(booking);
                })
                .onItem().invoke(persistedBooking -> {
                    LOG.infof("Booking criado com sucesso. ID=%s, Status=%s", persistedBooking.id, persistedBooking.status);
                })
                .onFailure().invoke(th -> {
                    LOG.error("Falha ao criar booking.", th);
                });
    }

    private Uni<Booking> persistBooking(Booking booking) {
        return Panache.withTransaction(() -> booking.persist())
                .replaceWith(booking);
    }


    private com.google.protobuf.Timestamp convertToTimestamp(java.time.LocalDate date) {
        if (date == null) {
            // Adicionando um log ou lançando uma exceção para informar que a data não foi fornecida
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

    public Uni<Void> deleteReservation(UUID id) {
        return Panache.withTransaction(() -> bookingRepository.deleteById(id))
                .replaceWithVoid()
                .onItem().invoke(() -> LOG.info("Reserva deletada com sucesso"));
    }
}