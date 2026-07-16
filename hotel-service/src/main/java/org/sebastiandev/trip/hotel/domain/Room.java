package org.sebastiandev.trip.hotel.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "rooms", uniqueConstraints = @UniqueConstraint(columnNames = {"hotel_id", "room_number"}))
public class Room {
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "hotel_id") public Hotel hotel;
    @Column(name = "room_number", nullable = false) public String roomNumber;
    @Column(name = "room_type", nullable = false) public String roomType;
    @Column(name = "nightly_price_minor", nullable = false) public long nightlyPriceMinor;
    @Column(nullable = false, length = 3) public String currency;
    @Column(nullable = false) public boolean active;
    @Version public long version;
}
