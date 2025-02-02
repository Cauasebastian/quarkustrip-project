package org.sebastianDev.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking extends PanacheEntityBase {

    @Id
    @GeneratedValue(generator = "assigned") // ✅ Estratégia "assigned" para IDs manuais
    @GenericGenerator(name = "assigned", strategy = "assigned")
    @Column(columnDefinition = "UUID")
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
    // Na classe Booking
    public void updateFrom(Booking source) {
        this.userId = source.userId;
        this.roomId = source.roomId;
        this.checkInDate = source.checkInDate;
        this.checkOutDate = source.checkOutDate;
        this.transportId = source.transportId;
        this.flightId = source.flightId;
        this.totalAmount = source.totalAmount;
        this.status = source.status;
    }
    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}