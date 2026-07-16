package org.sebastiandev.trip.flight.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.sebastiandev.trip.contracts.event.EventCodec;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.flight.repository.OutboxRepository;

@ApplicationScoped
public class OutboxService {
    @Inject ObjectMapper mapper;
    @Inject OutboxRepository repository;
    public Uni<EventEnvelope> enqueue(String topic, UUID bookingId, UUID causationId, Object payload) {
        EventEnvelope envelope = EventCodec.envelope(mapper, topic, bookingId, causationId, "flight-service", payload);
        OutboxEvent event = new OutboxEvent();
        event.id = envelope.eventId(); event.topic = topic; event.aggregateId = bookingId;
        event.payload = EventCodec.encode(mapper, envelope); event.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return repository.persist(event).replaceWith(envelope);
    }
}
