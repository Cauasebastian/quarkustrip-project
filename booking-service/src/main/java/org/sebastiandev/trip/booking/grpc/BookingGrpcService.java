package org.sebastiandev.trip.booking.grpc;

import io.grpc.Status;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.UUID;
import org.jboss.logging.Logger;
import org.sebastiandev.trip.booking.service.BookingApplicationService;
import org.sebastiandev.trip.booking.service.BookingMapper;
import org.sebastiandev.trip.booking.service.BookingValidator;
import org.sebastiandev.trip.contracts.grpc.BookingCommandService;
import org.sebastiandev.trip.contracts.grpc.CancelBookingRequest;
import org.sebastiandev.trip.contracts.grpc.CancelBookingResponse;
import org.sebastiandev.trip.contracts.grpc.CreateBookingRequest;
import org.sebastiandev.trip.contracts.grpc.CreateBookingResponse;
import org.sebastiandev.trip.contracts.grpc.GetBookingRequest;
import org.sebastiandev.trip.contracts.grpc.GetBookingResponse;
import org.sebastiandev.trip.contracts.grpc.ListUserBookingsRequest;
import org.sebastiandev.trip.contracts.grpc.ListUserBookingsResponse;

@GrpcService
public class BookingGrpcService implements BookingCommandService {
    private static final Logger LOG = Logger.getLogger(BookingGrpcService.class);

    @Inject BookingApplicationService service;
    @Inject BookingMapper mapper;
    @Inject BookingValidator validator;

    @Override
    public Uni<CreateBookingResponse> createBooking(CreateBookingRequest request) {
        return service.create(request).map(booking -> CreateBookingResponse.newBuilder()
                        .setBookingId(booking.id.toString())
                        .setStatus(org.sebastiandev.trip.contracts.grpc.BookingStatus.valueOf(booking.status.name()))
                        .setCreatedAt(mapper.timestamp(booking.createdAt)).build())
                .onFailure(failure -> !(failure instanceof IllegalArgumentException))
                        .invoke(failure -> LOG.error("Create booking RPC failed", failure))
                .onFailure(IllegalArgumentException.class).transform(this::invalidArgument);
    }

    @Override
    public Uni<GetBookingResponse> getBooking(GetBookingRequest request) {
        try {
            UUID bookingId = validator.parseUuid(request.getBookingId(), "bookingId");
            UUID requester = validator.parseUuid(request.getRequesterUserId(), "requesterUserId");
            return service.get(bookingId, requester, request.getAdmin())
                    .map(booking -> GetBookingResponse.newBuilder().setBooking(mapper.toView(booking)).build())
                    .onFailure(IllegalArgumentException.class).transform(this::notFound)
                    .onFailure(SecurityException.class).transform(this::permissionDenied);
        } catch (IllegalArgumentException exception) {
            return Uni.createFrom().failure(invalidArgument(exception));
        }
    }

    @Override
    public Uni<CancelBookingResponse> cancelBooking(CancelBookingRequest request) {
        try {
            return service.cancel(validator.parseUuid(request.getBookingId(), "bookingId"),
                            validator.parseUuid(request.getRequesterUserId(), "requesterUserId"), request.getAdmin(),
                            request.getReason())
                    .map(booking -> CancelBookingResponse.newBuilder().setBookingId(booking.id.toString())
                            .setStatus(org.sebastiandev.trip.contracts.grpc.BookingStatus.valueOf(booking.status.name()))
                            .build());
        } catch (IllegalArgumentException exception) {
            return Uni.createFrom().failure(invalidArgument(exception));
        }
    }

    @Override
    public Uni<ListUserBookingsResponse> listUserBookings(ListUserBookingsRequest request) {
        try {
            UUID userId = validator.parseUuid(request.getUserId(), "userId");
            int page = Math.max(0, request.getPage());
            int size = request.getSize() == 0 ? 20 : request.getSize();
            if (size < 1 || size > 100) {
                return Uni.createFrom().failure(Status.INVALID_ARGUMENT
                        .withDescription("size must be between 1 and 100").asRuntimeException());
            }
            return service.list(userId, page, size).map(result -> ListUserBookingsResponse.newBuilder()
                    .addAllBookings(result.bookings().stream().map(mapper::toView).toList())
                    .setPage(result.page())
                    .setSize(result.size())
                    .setTotalElements(result.totalElements())
                    .build());
        } catch (IllegalArgumentException exception) {
            return Uni.createFrom().failure(invalidArgument(exception));
        }
    }

    private Throwable invalidArgument(Throwable throwable) {
        return Status.INVALID_ARGUMENT.withDescription(throwable.getMessage()).asRuntimeException();
    }
    private Throwable notFound(Throwable throwable) {
        return Status.NOT_FOUND.withDescription(throwable.getMessage()).asRuntimeException();
    }
    private Throwable permissionDenied(Throwable throwable) {
        return Status.PERMISSION_DENIED.withDescription(throwable.getMessage()).asRuntimeException();
    }
}
