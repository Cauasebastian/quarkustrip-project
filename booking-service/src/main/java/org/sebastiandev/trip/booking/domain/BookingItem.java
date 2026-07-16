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
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_items")
public class BookingItem {
    @Id
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    public Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public BookingItemType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public BookingItemStatus status;

    @Column(name = "resource_id", nullable = false)
    public UUID resourceId;

    @Column(name = "request_data", nullable = false, columnDefinition = "jsonb")
    public String requestData;

    @Column(name = "reservation_id")
    public UUID reservationId;

    @Column(name = "amount_minor", nullable = false)
    public long amountMinor;

    @Column(name = "failure_reason")
    public String failureReason;

    @Version
    public long version;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;
}
