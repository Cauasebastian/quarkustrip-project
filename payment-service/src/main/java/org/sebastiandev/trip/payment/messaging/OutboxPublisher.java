package org.sebastiandev.trip.payment.messaging;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.vertx.core.Vertx;
import io.vertx.mutiny.pgclient.PgPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.logging.Logger;
import org.sebastiandev.trip.outbox.OutboxFallbackStartupGuard;
import org.sebastiandev.trip.outbox.ReactiveOutboxDispatcher;

@ApplicationScoped
public class OutboxPublisher {
    private static final Logger LOG = Logger.getLogger(OutboxPublisher.class);

    @Inject PgPool pool;
    @Inject Vertx vertx;
    @Inject @Channel("outbox") MutinyEmitter<String> emitter;
    @ConfigProperty(name = "trip.outbox.notify-enabled", defaultValue = "true") boolean notifyEnabled;
    @ConfigProperty(name = "trip.outbox.notification-channel", defaultValue = "trip_outbox") String channel;
    @ConfigProperty(name = "trip.outbox.batch-size", defaultValue = "50") int batchSize;
    @ConfigProperty(name = "trip.outbox.max-concurrency", defaultValue = "8") int maxConcurrency;

    private volatile ReactiveOutboxDispatcher dispatcher;

    void onStart(@Observes StartupEvent ignored) {
        dispatcher = new ReactiveOutboxDispatcher(pool, emitter, vertx, LOG, "payment-service", channel,
                batchSize, maxConcurrency, notifyEnabled);
        dispatcher.start();
        OutboxFallbackStartupGuard.ready();
    }

    void onStop(@Observes ShutdownEvent ignored) {
        OutboxFallbackStartupGuard.notReady();
        ReactiveOutboxDispatcher current = dispatcher;
        if (current != null) {
            current.stop();
        }
    }

    @Scheduled(every = "${trip.outbox.publish-interval:500ms}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            skipExecutionIf = OutboxFallbackStartupGuard.class)
    void fallback() {
        ReactiveOutboxDispatcher current = dispatcher;
        if (current != null) {
            current.request("fallback");
        }
    }
}
