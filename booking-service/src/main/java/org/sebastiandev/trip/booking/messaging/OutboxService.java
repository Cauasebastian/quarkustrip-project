package org.sebastiandev.trip.booking.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.sebastiandev.trip.booking.repository.OutboxRepository;
import org.sebastiandev.trip.contracts.event.EventCodec;
import org.sebastiandev.trip.contracts.event.EventEnvelope;

@ApplicationScoped
public class OutboxService {
    @Inject ObjectMapper mapper;
    @Inject OutboxRepository repository;

    public Uni<EventEnvelope> enqueue(String topic, UUID aggregateId, UUID causationId, Object payload) {
        EventEnvelope envelope = EventCodec.envelope(mapper, topic, aggregateId, causationId,
                "booking-service", payload);
        OutboxEvent event = new OutboxEvent();
        event.id = envelope.eventId();
        event.topic = topic;
        event.aggregateId = aggregateId;
        event.payload = EventCodec.encode(mapper, envelope);
        event.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return repository.persist(event).replaceWith(envelope);
    }
}
