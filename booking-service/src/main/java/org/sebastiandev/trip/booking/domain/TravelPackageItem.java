package org.sebastiandev.trip.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "travel_package_items")
public class TravelPackageItem {
    @Id
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id", nullable = false)
    public TravelPackage travelPackage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public BookingItemType type;

    @Column(name = "resource_id", nullable = false)
    public UUID resourceId;

    @Column(name = "request_data", nullable = false, columnDefinition = "jsonb")
    public String requestData;

    @Column(nullable = false, length = 3)
    public String currency;

    @Column(name = "amount_minor", nullable = false)
    public long amountMinor;

    @Column(nullable = false)
    public String label;

    public String detail;
}
