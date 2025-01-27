package org.sebastianDev.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking extends PanacheEntityBase {

    @Id
    @Column(columnDefinition = "UUID")
    @GeneratedValue(strategy = GenerationType.AUTO)
    public UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    public UUID userId;

    @Column(name = "room_id", nullable = false, columnDefinition = "UUID")
    public UUID roomId;

    @Column(name = "check_in_date", nullable = false)
    public LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    public LocalDate checkOutDate;

    @Column(name = "transport_id", nullable = false, columnDefinition = "UUID")
    public UUID transportId;

    @Column(name = "flight_id", nullable = false, columnDefinition = "UUID")
    public UUID flightId;

    @Column(name = "total_amount", nullable = false)
    public BigDecimal totalAmount;

    @Column(name = "status", nullable = false)
    public String status;


    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        status = "PENDING";
    }
    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}