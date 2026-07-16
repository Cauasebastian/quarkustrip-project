package org.sebastiandev.trip.booking.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    public UUID id;
    @Column(nullable = false)
    public String topic;
    @Column(name = "aggregate_id", nullable = false)
    public UUID aggregateId;
    @Column(nullable = false, columnDefinition = "jsonb")
    public String payload;
    @Column(nullable = false)
    public int attempts;
    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;
    @Column(name = "published_at")
    public OffsetDateTime publishedAt;
}
