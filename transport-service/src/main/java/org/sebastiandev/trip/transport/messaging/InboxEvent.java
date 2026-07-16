package org.sebastiandev.trip.transport.messaging; import jakarta.persistence.*; import java.time.OffsetDateTime; import java.util.UUID;
@Entity @Table(name="inbox_events") public class InboxEvent{@Id public UUID eventId;@Column(nullable=false)public String type;@Column(name="processed_at",nullable=false)public OffsetDateTime processedAt;}
