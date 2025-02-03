package org.sebastianDev.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "flight_reservations")
public class FlightReservation extends PanacheEntityBase {

    public enum ReservationStatus {
        BOOKED, CANCELLED
    }

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne
    @JoinColumn(name = "flight_seat_id", nullable = false)
    public FlightSeat seat;

    @Column(name = "booking_id", nullable = false)
    public UUID bookingId = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ReservationStatus status = ReservationStatus.BOOKED;

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