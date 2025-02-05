package org.sebastianDev.service;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.sebastianDev.*;
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

    @GrpcClient("flightService")
    MutinyFlightServiceGrpc.MutinyFlightServiceStub flightServiceClient;

    @Inject
    BookingRepository bookingRepository;

    /**
     * Creates a hotel booking via gRPC and persists in the database if successful.
     * @param booking
     * @return Uni<Booking>
     */
    @WithTransaction
    public Uni<Booking> createBooking(Booking booking) {
        if (booking.checkInDate == null || booking.checkOutDate == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Check-in date and check-out date must not be null"));
        }
        LOG.infof("Creating booking: %s", booking);

        // Generate ID if not already set
        if (booking.id == null) {
            booking.id = UUID.randomUUID(); // Ensure the ID is set
        }

        CheckAvailabilityRequest request = CheckAvailabilityRequest.newBuilder()
                .setBookingId(booking.id.toString())
                .setUserId(booking.userId.toString())
                .setRoomId(booking.roomId.toString())
                .setCheckInDate(convertToTimestamp(booking.checkInDate))
                .setCheckOutDate(convertToTimestamp(booking.checkOutDate))
                .build();

        LOG.infof("Checking availability: %s", request);
        return availabilityClient.checkAvailabilityAndOccupy(request)
                .onItem().transformToUni(response -> {
                    if (!response.getIsAvailable()) {
                        return Uni.createFrom().failure(new RuntimeException("HotelService: " + response.getMessage()));
                    }
                    LOG.infof("Room available. Persisting booking: %s", booking);

                    // After room is available, attempt to reserve seat on the flight
                    return reserveSeatOnFlight(booking)
                            .onItem().transformToUni(seatResponse -> {
                                if (!seatResponse.getSuccess()) {
                                    return Uni.createFrom().failure(new RuntimeException("Flight seat reservation failed: " + seatResponse.getMessage()));
                                }

                                // Persist the booking if seat reservation was successful
                                return persistBooking(booking)
                                        .onFailure().call(th -> {
                                            LOG.errorf("Persist failed, canceling reservation: %s", th.getMessage());
                                            return cancelReservationViaGrpc(booking.roomId, booking.id)
                                                    .onFailure().invoke(e -> LOG.errorf("gRPC cancelation failed: %s", e.getMessage()))
                                                    .replaceWith(Uni.createFrom().failure(th))
                                                    .onItem().transformToUni(ignored -> Uni.createFrom().failure(th));
                                        });
                            });
                })
                .onItem().invoke(persistedBooking ->
                        LOG.infof("Booking successfully created. ID=%s", persistedBooking.id))
                .onFailure().invoke(th ->
                        LOG.errorf("Failed to create booking: %s", th.getMessage()));
    }

    /**
     * Makes a gRPC call to reserve a flight seat.
     * @param booking
     * @return Uni<ReserveSeatResponse>
     */
    private Uni<ReserveSeatResponse> reserveSeatOnFlight(Booking booking) {
        // Prepare the request to reserve a seat
        ReserveSeatRequest request = ReserveSeatRequest.newBuilder()
                .setFlightId(booking.flightId.toString())
                .setSeatNumber(booking.seatNumber)
                .setUserId(booking.userId.toString())
                .build();

        LOG.infof("Reserving seat: %s", request);

        // Call the flight service gRPC method to reserve a seat
        return flightServiceClient.reserveSeat(request);
    }

    /**
     * Cancels a reservation via gRPC.
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
     * Persists a booking in the database.
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
     * Converts a LocalDate to a Timestamp.
     * @param date
     * @return Timestamp
     */
    private com.google.protobuf.Timestamp convertToTimestamp(java.time.LocalDate date) {
        if (date == null) {
            LOG.error("Provided date is null!");
            throw new IllegalArgumentException("Provided date is null!");
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
                .onItem().ifNull().failWith(new RuntimeException("Booking not found with ID: " + id))
        );
    }

    /**
     * Deletes a hotel reservation via gRPC and deletes the booking from the database.
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
