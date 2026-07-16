package org.sebastiandev.trip.booking.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public BookingStatus status;

    @Column(nullable = false, length = 3)
    public String currency;

    @Column(name = "total_amount_minor", nullable = false)
    public long totalAmountMinor;

    @Column(name = "payment_method_ref", nullable = false)
    public String paymentMethodRef;

    @Column(name = "payment_id")
    public UUID paymentId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    public String idempotencyKey;

    @Column(name = "step_deadline", nullable = false)
    public OffsetDateTime stepDeadline;

    @Column(name = "saga_deadline", nullable = false)
    public OffsetDateTime sagaDeadline;

    @Column(name = "failure_code")
    public String failureCode;

    @Column(name = "cancellation_requested", nullable = false)
    public boolean cancellationRequested;

    @Version
    public long version;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    public List<BookingItem> items = new ArrayList<>();

    public boolean allItems(BookingItemStatus expected) {
        return !items.isEmpty() && items.stream().allMatch(item -> item.status == expected);
    }

    public boolean hasItemFailure() {
        return items.stream().anyMatch(item -> item.status == BookingItemStatus.FAILED);
    }
}
