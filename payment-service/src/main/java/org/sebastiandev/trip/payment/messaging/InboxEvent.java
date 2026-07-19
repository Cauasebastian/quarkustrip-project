package org.sebastiandev.trip.payment.messaging; import jakarta.persistence.*; import java.time.OffsetDateTime; import java.util.UUID;
@Entity @Table(name="inbox_events") public class InboxEvent{@Id @Column(name="event_id") public UUID eventId;@Column(nullable=false)public String type;@Column(name="processed_at",nullable=false)public OffsetDateTime processedAt;}
