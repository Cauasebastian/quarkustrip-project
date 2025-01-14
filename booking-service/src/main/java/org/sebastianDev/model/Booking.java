package org.sebastianDev.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
@Table(name = "bookings")
public class Booking extends PanacheEntityBase {

    @Id
    @Column(columnDefinition = "UUID")
    public UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "UUID")
    public UUID userId;

    @Column(name = "room_id", nullable = false, columnDefinition = "UUID")
    public UUID roomId;

    @Column(name = "transport_id", nullable = false, columnDefinition = "UUID")
    public UUID transportId;

    @Column(name = "flight_id", nullable = false, columnDefinition = "UUID")
    public UUID flightId;

    @Column(name = "total_amount", nullable = false)
    public BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    //updated at
    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}