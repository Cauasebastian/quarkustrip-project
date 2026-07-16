package org.sebastiandev.trip.flight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "flight_seats", uniqueConstraints = @UniqueConstraint(columnNames = {"flight_id", "seat_number"}))
public class FlightSeat {
    public enum Status { AVAILABLE, HELD, CONFIRMED }
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "flight_id") public Flight flight;
    @Column(name = "seat_number", nullable = false) public String seatNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Status status;
    @Column(name = "held_by_item_id") public UUID heldByItemId;
    @Version public long version;
}
