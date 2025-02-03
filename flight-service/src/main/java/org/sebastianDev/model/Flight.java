package org.sebastianDev.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "flights")
public class Flight extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "flight_number", nullable = false, length = 10)
    public String flightNumber;

    @Column(nullable = false, length = 3)
    public String origin;

    @Column(nullable = false, length = 3)
    public String destination;

    @Column(name = "departure_time", nullable = false)
    public OffsetDateTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    public OffsetDateTime arrivalTime;

    @Column(name = "total_seats", nullable = false)
    public int totalSeats;

    @OneToMany(mappedBy = "flight", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<FlightSeat> seats;

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