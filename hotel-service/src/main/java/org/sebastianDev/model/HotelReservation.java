package org.sebastianDev.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;


@Entity
@Table(name = "hotel_reservations")
public class HotelReservation extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    public Room room;

    @Column(name = "booking_id", nullable = false)
    public UUID bookingId; // Referência ao Booking-Service

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "check_in_date", nullable = false)
    public LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    public LocalDate checkOutDate;

    @Column(nullable = false, length = 50)
    public String status; // e.g., 'Booked', 'Cancelled'

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}