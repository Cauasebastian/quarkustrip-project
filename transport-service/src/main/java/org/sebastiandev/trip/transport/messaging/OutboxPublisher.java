package org.sebastiandev.trip.transport.messaging;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.sebastiandev.trip.contracts.observability.TraceContextSnapshot;
import org.sebastiandev.trip.contracts.observability.TraceContextSupport;
import org.sebastiandev.trip.transport.repository.OutboxRepository;

@ApplicationScoped
public class OutboxPublisher {
    @Inject OutboxRepository repository;
    @Inject @Channel("outbox") MutinyEmitter<String> emitter;

    @Scheduled(every = "${trip.outbox.publish-interval:1s}",
            delayed = "${trip.outbox.initial-delay:10s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @WithSession
    Uni<Void> publish() {
        return repository.find("publishedAt is null order by createdAt").page(0, 50).list().chain(this::publishBatch);
    }

    private Uni<Void> publishBatch(List<OutboxEvent> events) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (OutboxEvent event : events) chain = chain.chain(() -> publishOne(event));
        return chain;
    }

    private Uni<Void> publishOne(OutboxEvent event) {
        TraceContextSupport.OutboxPublishTrace trace = TraceContextSupport.beginOutboxPublish(event.id,
                event.aggregateId, event.topic, event.attempts + 1, event.createdAt.toInstant(),
                new TraceContextSnapshot(event.traceParent, event.traceState));
        Message<String> message = KafkaRecord.of(event.topic, event.aggregateId.toString(), event.payload)
                .withHeader(TraceContextSupport.OUTBOX_ATTEMPT, Integer.toString(event.attempts + 1))
                .addMetadata(TracingMetadata.withCurrent(trace.context()));
        return emitter.sendMessage(message).onItemOrFailure().invoke((ignored, failure) -> trace.finish(failure))
                .chain(() -> markPublished(event)).onFailure().call(() -> incrementAttempts(event));
    }

    private Uni<Void> markPublished(OutboxEvent event) {
        return Panache.withTransaction(() -> repository.findById(event.id).onItem().ifNotNull().invoke(stored -> {
            stored.publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
            stored.attempts++;
        })).replaceWithVoid();
    }

    private Uni<Void> incrementAttempts(OutboxEvent event) {
        return Panache.withTransaction(() -> repository.findById(event.id).onItem().ifNotNull()
                .invoke(stored -> stored.attempts++)).replaceWithVoid();
    }
}
