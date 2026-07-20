package org.sebastiandev.trip.booking.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dlq_events")
public class DlqEvent {
    @Id
    public String id;

    @Column(name = "event_id", nullable = false)
    public UUID eventId;

    @Column(name = "original_topic", nullable = false)
    public String originalTopic;

    @Column(name = "processed_at", nullable = false)
    public OffsetDateTime processedAt;
}
