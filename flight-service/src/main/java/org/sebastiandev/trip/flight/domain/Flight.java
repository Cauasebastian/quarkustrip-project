package org.sebastiandev.trip.flight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "flights")
public class Flight {
    @Id public UUID id;
    @Column(name = "flight_number", nullable = false, unique = true) public String flightNumber;
    @Column(nullable = false, length = 3) public String origin;
    @Column(nullable = false, length = 3) public String destination;
    @Column(name = "departure_time", nullable = false) public OffsetDateTime departureTime;
    @Column(name = "arrival_time", nullable = false) public OffsetDateTime arrivalTime;
    @Column(name = "seat_price_minor", nullable = false) public long seatPriceMinor;
    @Column(nullable = false, length = 3) public String currency;
    @Version public long version;
}
