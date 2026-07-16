package org.sebastiandev.trip.hotel.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "hotel_reservations")
public class HotelReservation {
    public enum Status { HELD, CONFIRMED, CANCELLED, EXPIRED }
    @Id public UUID id;
    @Column(name = "booking_id", nullable = false) public UUID bookingId;
    @Column(name = "booking_item_id", nullable = false, unique = true) public UUID bookingItemId;
    @Column(name = "user_id", nullable = false) public UUID userId;
    @Column(name = "room_id", nullable = false) public UUID roomId;
    @Column(name = "check_in", nullable = false) public LocalDate checkIn;
    @Column(name = "check_out", nullable = false) public LocalDate checkOut;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Status status;
    @Column(name = "amount_minor", nullable = false) public long amountMinor;
    @Column(nullable = false, length = 3) public String currency;
    @Column(name = "hold_until", nullable = false) public OffsetDateTime holdUntil;
    @Column(name = "created_at", nullable = false) public OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) public OffsetDateTime updatedAt;
    @Version public long version;
}
