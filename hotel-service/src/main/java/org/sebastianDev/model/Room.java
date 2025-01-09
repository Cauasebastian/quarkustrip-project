package org.sebastianDev.model;


import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(columnNames = {"hotel_id", "room_number"}))
public class Room extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne
    @JoinColumn(name = "hotel_id", nullable = false)
    public Hotel hotel;

    @Column(name = "room_number", nullable = false, length = 10)
    public String roomNumber;

    @Column(name = "room_type", length = 50)
    public String roomType; // e.g., 'Single', 'Double', 'Suite'

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal price;

    @Column(name = "is_available")
    public Boolean isAvailable = true;

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
