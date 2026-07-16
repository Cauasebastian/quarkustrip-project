package org.sebastiandev.trip.flight.messaging;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.sebastiandev.trip.flight.repository.OutboxRepository;

@ApplicationScoped
public class OutboxPublisher {
    @Inject OutboxRepository repository;
    @Inject @Channel("outbox") MutinyEmitter<String> emitter;
    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    Uni<Void> publish() {
        return repository.find("publishedAt is null order by createdAt").page(0, 50).list()
                .chain(this::publishBatch);
    }
    private Uni<Void> publishBatch(List<OutboxEvent> events) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (OutboxEvent event : events) {
            chain = chain.chain(() -> emitter.sendMessage(KafkaRecord.of(event.topic, event.aggregateId.toString(), event.payload))
                    .chain(() -> Panache.withTransaction(() -> repository.findById(event.id).invoke(stored -> {
                        stored.publishedAt = OffsetDateTime.now(ZoneOffset.UTC); stored.attempts++;
                    })).replaceWithVoid())
                    .onFailure().call(() -> Panache.withTransaction(() -> repository.findById(event.id)
                            .invoke(stored -> stored.attempts++)).replaceWithVoid()));
        }
        return chain;
    }
}
