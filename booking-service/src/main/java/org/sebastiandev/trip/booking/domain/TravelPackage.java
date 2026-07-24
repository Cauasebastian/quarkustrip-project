package org.sebastiandev.trip.booking.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "travel_packages")
public class TravelPackage {
    @Id
    public UUID id;

    @Column(nullable = false)
    public String name;

    public String description;

    @Column(nullable = false, length = 3)
    public String currency;

    @Column(name = "created_by_user_id", nullable = false)
    public UUID createdByUserId;

    @Column(nullable = false)
    public boolean active;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "travelPackage", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
    public List<TravelPackageItem> items = new ArrayList<>();
}
