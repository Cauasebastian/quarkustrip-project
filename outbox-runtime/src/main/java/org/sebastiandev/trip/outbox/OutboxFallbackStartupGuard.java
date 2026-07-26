package org.sebastiandev.trip.outbox;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prevents the sub-second fallback from instantiating its publisher before
 * Reactive Messaging has finished connecting the outgoing Kafka channel.
 */
public final class OutboxFallbackStartupGuard implements Scheduled.SkipPredicate {
    private static final AtomicBoolean READY = new AtomicBoolean();

    public static void ready() {
        READY.set(true);
    }

    public static void notReady() {
        READY.set(false);
    }

    @Override
    public boolean test(ScheduledExecution execution) {
        return !READY.get();
    }
}