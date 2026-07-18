package org.sebastiandev.trip.user.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.sebastiandev.trip.contracts.event.EventCodec;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.observability.TraceContextSnapshot;
import org.sebastiandev.trip.contracts.observability.TraceContextSupport;
import org.sebastiandev.trip.user.repository.OutboxRepository;

@ApplicationScoped
public class OutboxService {
    @Inject ObjectMapper mapper;
    @Inject OutboxRepository repository;

    public Uni<EventEnvelope> enqueue(String topic, UUID aggregateId, UUID causationId, Object payload) {
        EventEnvelope envelope = EventCodec.envelope(mapper, topic, aggregateId, causationId,
                "user-service", payload);
        TraceContextSnapshot trace = TraceContextSupport.captureCurrent();
        OutboxEvent event = new OutboxEvent();
        event.id = envelope.eventId();
        event.topic = topic;
        event.aggregateId = aggregateId;
        event.payload = EventCodec.encode(mapper, envelope);
        event.traceParent = trace.traceParent();
        event.traceState = trace.traceState();
        event.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return repository.persist(event).replaceWith(envelope);
    }
}
