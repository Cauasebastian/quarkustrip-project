package org.sebastiandev.trip.booking.service;

import com.google.protobuf.Timestamp;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import org.sebastiandev.trip.booking.domain.Booking;
import org.sebastiandev.trip.booking.domain.BookingItem;
import org.sebastiandev.trip.contracts.grpc.BookingItemView;
import org.sebastiandev.trip.contracts.grpc.BookingView;
import org.sebastiandev.trip.contracts.grpc.Money;

@ApplicationScoped
public class BookingMapper {
    public BookingView toView(Booking booking) {
        BookingView.Builder builder = BookingView.newBuilder()
                .setId(booking.id.toString())
                .setUserId(booking.userId.toString())
                .setStatus(org.sebastiandev.trip.contracts.grpc.BookingStatus.valueOf(booking.status.name()))
                .setTotal(Money.newBuilder().setCurrency(booking.currency).setAmountMinor(booking.totalAmountMinor))
                .setCreatedAt(timestamp(booking.createdAt))
                .setUpdatedAt(timestamp(booking.updatedAt));
        if (booking.failureCode != null) builder.setFailureCode(booking.failureCode);
        booking.items.forEach(item -> builder.addItems(toView(item, booking.currency)));
        return builder.build();
    }

    private BookingItemView toView(BookingItem item, String currency) {
        BookingItemView.Builder builder = BookingItemView.newBuilder()
                .setId(item.id.toString())
                .setType(org.sebastiandev.trip.contracts.grpc.BookingItemType.valueOf(item.type.name()))
                .setResourceId(item.resourceId.toString())
                .setStatus(switch (item.status) {
                    case PENDING -> org.sebastiandev.trip.contracts.grpc.BookingItemStatus.PENDING;
                    case HELD -> org.sebastiandev.trip.contracts.grpc.BookingItemStatus.HELD;
                    case CONFIRMED -> org.sebastiandev.trip.contracts.grpc.BookingItemStatus.ITEM_CONFIRMED;
                    case FAILED -> org.sebastiandev.trip.contracts.grpc.BookingItemStatus.ITEM_FAILED;
                    case CANCELLED -> org.sebastiandev.trip.contracts.grpc.BookingItemStatus.ITEM_CANCELLED;
                })
                .setPrice(Money.newBuilder().setCurrency(currency).setAmountMinor(item.amountMinor));
        if (item.reservationId != null) builder.setExternalReservationId(item.reservationId.toString());
        if (item.failureReason != null) builder.setFailureReason(item.failureReason);
        return builder.build();
    }

    public Timestamp timestamp(OffsetDateTime value) {
        return Timestamp.newBuilder().setSeconds(value.toEpochSecond()).setNanos(value.getNano()).build();
    }
}
