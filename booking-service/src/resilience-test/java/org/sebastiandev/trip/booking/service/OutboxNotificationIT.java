package org.sebastiandev.trip.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sebastiandev.trip.booking.messaging.OutboxService;
import org.sebastiandev.trip.booking.repository.OutboxRepository;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.event.TopicNames;

@QuarkusTest
@QuarkusTestResource(BookingPostgresTestResource.class)
class OutboxNotificationIT {
    @Inject OutboxService outboxService;
    @Inject OutboxRepository repository;
    @Inject @Connector(InMemoryConnector.CONNECTOR) InMemoryConnector connector;

    private InMemorySink<String> sink;

    @BeforeEach
    void setUp() {
        sink = connector.sink("outbox");
        sink.clear();
    }

    @Test
    @RunOnVertxContext
    void committedInsertWakesPublisherWithoutScheduler(UniAsserter asserter) {
        AtomicLong committedAt = new AtomicLong();
        asserter.execute(() -> Panache.withTransaction(repository::deleteAll).replaceWithVoid());
        asserter.execute(() -> Panache.withTransaction(() -> outboxService.enqueue(TopicNames.BOOKING_CREATED,
                        UUID.randomUUID(), null, Map.of("source", "notify-test")))
                .invoke(ignored -> committedAt.set(System.nanoTime())).replaceWithVoid());
        asserter.assertThat(() -> waitForMessages(1, 100), count -> {
            assertEquals(1, count);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - committedAt.get()).toMillis();
            assertTrue(elapsedMs < 1_000, "notification dispatch took " + elapsedMs + " ms");
        });
        asserter.assertThat(() -> Panache.withSession(() -> repository.count("publishedAt is not null")),
                count -> assertEquals(1L, count));
    }

    @Test
    @RunOnVertxContext
    void rolledBackInsertDoesNotNotifyOrPublish(UniAsserter asserter) {
        asserter.execute(() -> Panache.withTransaction(repository::deleteAll).replaceWithVoid());
        asserter.execute(() -> Panache.withTransaction(() -> outboxService.enqueue(TopicNames.BOOKING_CREATED,
                        UUID.randomUUID(), null, Map.of("source", "rollback-test"))
                .chain(() -> Uni.createFrom().failure(new ExpectedRollback())))
                .onFailure(ExpectedRollback.class).recoverWithNull().replaceWithVoid());
        asserter.assertThat(() -> Uni.createFrom().voidItem().onItem().delayIt().by(Duration.ofMillis(700))
                        .map(ignored -> sink.received().size()),
                count -> assertEquals(0, count));
        asserter.assertThat(() -> Panache.withSession(repository::count), count -> assertEquals(0L, count));
    }

    @Test
    @RunOnVertxContext
    void drainsMoreThanOneBatchAndPreservesGroupOrder(UniAsserter asserter) {
        List<UUID> expectedOrder = new ArrayList<>();
        UUID bookingId = UUID.randomUUID();
        asserter.execute(() -> Panache.withTransaction(repository::deleteAll).replaceWithVoid());
        asserter.execute(() -> Panache.withTransaction(() -> {
            Uni<Void> chain = Uni.createFrom().voidItem();
            for (int index = 0; index < 60; index++) {
                int sequence = index;
                chain = chain.chain(() -> outboxService.enqueue(TopicNames.BOOKING_CREATED, bookingId, null,
                                Map.of("sequence", sequence))
                        .invoke(envelope -> expectedOrder.add(envelope.eventId())).replaceWithVoid());
            }
            return chain;
        }));
        asserter.assertThat(() -> waitForMessages(60, 250), count -> assertEquals(60, count));
        asserter.assertThat(() -> Uni.createFrom().item(this::receivedEventIds),
                actualOrder -> assertEquals(expectedOrder, actualOrder));
        asserter.assertThat(() -> Panache.withSession(() -> repository.count("publishedAt is not null")),
                count -> assertEquals(60L, count));
    }

    private Uni<Integer> waitForMessages(int target, int remainingAttempts) {
        int received = sink.received().size();
        if (received >= target) {
            return Uni.createFrom().item(received);
        }
        if (remainingAttempts <= 0) {
            return Uni.createFrom().failure(new AssertionError(
                    "Expected " + target + " outbox messages, received " + received));
        }
        return Uni.createFrom().voidItem().onItem().delayIt().by(Duration.ofMillis(20))
                .chain(() -> waitForMessages(target, remainingAttempts - 1));
    }

    private List<UUID> receivedEventIds() {
        return sink.received().stream().map(Message::getPayload).map(payload -> {
            int start = payload.indexOf("\"eventId\":\"") + 11;
            return UUID.fromString(payload.substring(start, start + 36));
        }).toList();
    }

    private static final class ExpectedRollback extends RuntimeException {
    }
}
