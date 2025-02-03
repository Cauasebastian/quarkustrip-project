package org.sebastianDev.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "flight_seats")
public class FlightSeat extends PanacheEntityBase {

    public enum SeatStatus {
        AVAILABLE, BOOKED, CANCELLED
    }

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne
    @JoinColumn(name = "flight_id", nullable = false)
    public Flight flight;

    @Column(name = "seat_number", nullable = false, length = 10)
    public String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SeatStatus status = SeatStatus.AVAILABLE;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}