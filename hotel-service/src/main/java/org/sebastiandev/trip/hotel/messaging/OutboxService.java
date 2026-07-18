package org.sebastiandev.trip.hotel.messaging;
import com.fasterxml.jackson.databind.ObjectMapper; import io.smallrye.mutiny.Uni; import jakarta.enterprise.context.ApplicationScoped; import jakarta.inject.Inject;
import java.time.OffsetDateTime; import java.time.ZoneOffset; import java.util.UUID;
import org.sebastiandev.trip.contracts.event.*; import org.sebastiandev.trip.contracts.observability.*; import org.sebastiandev.trip.hotel.repository.OutboxRepository;
@ApplicationScoped public class OutboxService {
 @Inject ObjectMapper mapper; @Inject OutboxRepository repository;
 public Uni<EventEnvelope> enqueue(String topic, UUID aggregateId, UUID cause, Object payload) {
  EventEnvelope envelope=EventCodec.envelope(mapper,topic,aggregateId,cause,"hotel-service",payload); OutboxEvent event=new OutboxEvent();
  event.id=envelope.eventId(); event.topic=topic; event.aggregateId=aggregateId; event.payload=EventCodec.encode(mapper,envelope);
  TraceContextSnapshot trace=TraceContextSupport.captureCurrent(); event.traceParent=trace.traceParent(); event.traceState=trace.traceState(); event.createdAt=OffsetDateTime.now(ZoneOffset.UTC);
  return repository.persist(event).replaceWith(envelope);
 }
}
