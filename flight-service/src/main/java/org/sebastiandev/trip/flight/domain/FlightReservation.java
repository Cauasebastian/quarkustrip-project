package org.sebastiandev.trip.flight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "flight_reservations")
public class FlightReservation {
    public enum Status { HELD, CONFIRMED, CANCELLED, EXPIRED }
    @Id public UUID id;
    @Column(name = "booking_id", nullable = false) public UUID bookingId;
    @Column(name = "booking_item_id", nullable = false, unique = true) public UUID bookingItemId;
    @Column(name = "user_id", nullable = false) public UUID userId;
    @Column(name = "flight_id", nullable = false) public UUID flightId;
    @Column(name = "seat_id", nullable = false) public UUID seatId;
    @Column(name = "seat_number", nullable = false) public String seatNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Status status;
    @Column(name = "amount_minor", nullable = false) public long amountMinor;
    @Column(nullable = false, length = 3) public String currency;
    @Column(name = "hold_until", nullable = false) public OffsetDateTime holdUntil;
    @Column(name = "created_at", nullable = false) public OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) public OffsetDateTime updatedAt;
    @Version public long version;
}
