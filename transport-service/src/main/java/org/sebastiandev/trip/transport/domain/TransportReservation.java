package org.sebastiandev.trip.transport.domain;
import jakarta.persistence.*; import java.time.OffsetDateTime; import java.util.UUID;
@Entity @Table(name="transport_reservations") public class TransportReservation {
 public enum Status{HELD,CONFIRMED,CANCELLED,EXPIRED} @Id public UUID id; @Column(name="booking_id",nullable=false) public UUID bookingId;
 @Column(name="booking_item_id",nullable=false,unique=true) public UUID bookingItemId; @Column(name="user_id",nullable=false) public UUID userId;
 @Column(name="offer_id",nullable=false) public UUID offerId; @Column(name="starts_at",nullable=false) public OffsetDateTime startsAt; @Column(name="ends_at",nullable=false) public OffsetDateTime endsAt;
 @Enumerated(EnumType.STRING) @Column(nullable=false) public Status status; @Column(name="amount_minor",nullable=false) public long amountMinor; @Column(nullable=false,length=3) public String currency;
 @Column(name="hold_until",nullable=false) public OffsetDateTime holdUntil; @Column(name="created_at",nullable=false) public OffsetDateTime createdAt; @Column(name="updated_at",nullable=false) public OffsetDateTime updatedAt; @Version public long version;
}
