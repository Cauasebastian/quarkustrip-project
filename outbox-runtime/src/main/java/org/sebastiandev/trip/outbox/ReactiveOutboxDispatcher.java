package org.sebastiandev.trip.outbox;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.TracingMetadata;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.pgclient.PgConnection;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import org.sebastiandev.trip.contracts.observability.TraceContextSnapshot;
import org.sebastiandev.trip.contracts.observability.TraceContextSupport;

/**
 * Event-driven transactional outbox dispatcher. PostgreSQL notifications are
 * only wake-up hints; pending rows remain the source of truth.
 */
public final class ReactiveOutboxDispatcher {
    private static final String FETCH_PENDING = """
            SELECT id, topic, aggregate_id, payload, attempts, created_at, trace_parent, trace_state
              FROM outbox_events
             WHERE published_at IS NULL
             ORDER BY sequence_no, id
             LIMIT $1
            """;
    private static final String MARK_PUBLISHED = """
            UPDATE outbox_events
               SET published_at = $1, attempts = attempts + 1
             WHERE id = $2 AND published_at IS NULL
            """;
    private static final String MARK_FAILED = """
            UPDATE outbox_events
               SET attempts = attempts + 1
             WHERE id = $1 AND published_at IS NULL
            """;
    private static final long INITIAL_RECONNECT_DELAY_MS = 100;
    private static final long MAX_RECONNECT_DELAY_MS = 5_000;

    private final PgPool pool;
    private final MutinyEmitter<String> emitter;
    private final Vertx vertx;
    private final Logger log;
    private final String serviceName;
    private final String notificationChannel;
    private final int batchSize;
    private final int maxConcurrency;
    private final boolean notifyEnabled;
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean requested = new AtomicBoolean();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicReference<String> pendingTrigger = new AtomicReference<>("startup");
    private final AtomicLong reconnectDelayMs = new AtomicLong(INITIAL_RECONNECT_DELAY_MS);
    private volatile PgConnection listenerConnection;

    public ReactiveOutboxDispatcher(PgPool pool, MutinyEmitter<String> emitter, Vertx vertx, Logger log,
            String serviceName, String notificationChannel, int batchSize, int maxConcurrency,
            boolean notifyEnabled) {
        this.pool = Objects.requireNonNull(pool);
        this.emitter = Objects.requireNonNull(emitter);
        this.vertx = Objects.requireNonNull(vertx);
        this.log = Objects.requireNonNull(log);
        this.serviceName = Objects.requireNonNull(serviceName);
        this.notificationChannel = validChannel(notificationChannel);
        this.batchSize = Math.max(1, batchSize);
        this.maxConcurrency = Math.max(1, maxConcurrency);
        this.notifyEnabled = notifyEnabled;
    }

    public void start() {
        if (!notifyEnabled) {
            initialized.set(true);
            request("startup");
            return;
        }
        connectListener();
    }

    public void request(String trigger) {
        if (stopped.get()) {
            return;
        }
        pendingTrigger.set(trigger == null || trigger.isBlank() ? "unknown" : trigger);
        requested.set(true);
        if (initialized.get()) {
            launchIfIdle();
        }
    }

    public void stop() {
        stopped.set(true);
        PgConnection connection = listenerConnection;
        listenerConnection = null;
        if (connection != null) {
            connection.close().subscribe().with(ignored -> {
            }, failure -> log.debug("Could not close PostgreSQL outbox listener", failure));
        }
    }

    private void connectListener() {
        if (stopped.get()) {
            return;
        }
        pool.getConnection()
                .map(PgConnection::cast)
                .invoke(connection -> {
                    connection.notificationHandler(notification -> {
                        if (notificationChannel.equals(notification.getChannel())) {
                            request("notify");
                        }
                    });
                    connection.exceptionHandler(this::listenerFailed);
                    connection.closeHandler(() -> listenerFailed(
                            new IllegalStateException("PostgreSQL outbox listener connection closed")));
                })
                .call(connection -> connection.query("LISTEN " + notificationChannel).execute())
                .subscribe().with(connection -> {
                    listenerConnection = connection;
                    reconnectDelayMs.set(INITIAL_RECONNECT_DELAY_MS);
                    reconnectScheduled.set(false);
                    initialized.set(true);
                    request("startup");
                }, failure -> {
                    initialized.set(true);
                    log.warnf("PostgreSQL outbox notifications unavailable for %s; using fallback: %s",
                            serviceName, safeMessage(failure));
                    request("fallback");
                    scheduleReconnect();
                });
    }

    private void listenerFailed(Throwable failure) {
        if (stopped.get()) {
            return;
        }
        listenerConnection = null;
        log.warnf("PostgreSQL outbox listener interrupted for %s; using fallback: %s",
                serviceName, safeMessage(failure));
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (stopped.get() || !notifyEnabled || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        long delay = reconnectDelayMs.getAndUpdate(current -> Math.min(current * 2, MAX_RECONNECT_DELAY_MS));
        vertx.setTimer(delay, ignored -> {
            reconnectScheduled.set(false);
            connectListener();
        });
    }

    private void launchIfIdle() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        requested.set(false);
        String trigger = pendingTrigger.getAndSet("notify");
        drainUntilSettled(trigger).subscribe().with(
                ignored -> finishRun(),
                failure -> {
                    log.errorf(failure, "Outbox dispatcher failed for %s", serviceName);
                    finishRun();
                });
    }

    private void finishRun() {
        if (requested.getAndSet(false)) {
            String trigger = pendingTrigger.getAndSet("notify");
            drainUntilSettled(trigger).subscribe().with(
                    ignored -> finishRun(),
                    failure -> {
                        log.errorf(failure, "Outbox dispatcher failed for %s", serviceName);
                        finishRun();
                    });
            return;
        }
        running.set(false);
        if (requested.get()) {
            launchIfIdle();
        }
    }

    private Uni<Void> drainUntilSettled(String trigger) {
        return fetchPending().chain(events -> {
            if (events.isEmpty()) {
                return Uni.createFrom().voidItem();
            }
            return dispatchBatch(events, trigger).chain(result -> {
                if (result.failureCount() > 0) {
                    return Uni.createFrom().voidItem();
                }
                return drainUntilSettled(trigger);
            });
        });
    }

    private Uni<List<PendingEvent>> fetchPending() {
        return pool.preparedQuery(FETCH_PENDING).execute(Tuple.of(batchSize)).map(rows -> {
            List<PendingEvent> events = new ArrayList<>(rows.size());
            for (Row row : rows) {
                Object payload = row.getValue("payload");
                String encodedPayload = payload instanceof JsonObject json ? json.encode() : String.valueOf(payload);
                events.add(new PendingEvent(
                        row.getUUID("id"), row.getString("topic"), row.getUUID("aggregate_id"), encodedPayload,
                        row.getInteger("attempts"), row.getOffsetDateTime("created_at"),
                        row.getString("trace_parent"), row.getString("trace_state")));
            }
            return events;
        });
    }

    private Uni<BatchResult> dispatchBatch(List<PendingEvent> events, String trigger) {
        Map<GroupKey, List<PendingEvent>> grouped = new LinkedHashMap<>();
        for (PendingEvent event : events) {
            grouped.computeIfAbsent(new GroupKey(event.topic(), event.aggregateId()), ignored -> new ArrayList<>())
                    .add(event);
        }

        DispatchMetadata metadata = new DispatchMetadata(trigger, events.size(), grouped.size(),
                Math.min(grouped.size(), maxConcurrency));
        return Multi.createFrom().iterable(grouped.values())
                .onItem().transformToUni(group -> publishGroup(group, metadata)).merge(maxConcurrency)
                .collect().asList()
                .map(groupResults -> groupResults.stream().flatMap(List::stream).toList())
                .chain(outcomes -> {
                    BatchResult result = BatchResult.from(outcomes);
                    return persistOutcomes(outcomes)
                            .onItemOrFailure().invoke((ignored, failure) -> finishTraces(outcomes, result, failure))
                            .replaceWith(result);
                });
    }

    private Uni<List<PublishOutcome>> publishGroup(List<PendingEvent> events, DispatchMetadata metadata) {
        return publishGroup(events, metadata, 0, new ArrayList<>());
    }

    private Uni<List<PublishOutcome>> publishGroup(List<PendingEvent> events, DispatchMetadata metadata, int index,
            List<PublishOutcome> outcomes) {
        if (index >= events.size()) {
            return Uni.createFrom().item(outcomes);
        }
        return publishOne(events.get(index), metadata).chain(outcome -> {
            outcomes.add(outcome);
            if (!outcome.success()) {
                return Uni.createFrom().item(outcomes);
            }
            return publishGroup(events, metadata, index + 1, outcomes);
        });
    }

    private Uni<PublishOutcome> publishOne(PendingEvent event, DispatchMetadata metadata) {
        TraceContextSupport.OutboxPublishTrace trace = TraceContextSupport.beginOutboxPublish(event.id(),
                event.aggregateId(), event.topic(), event.attempts() + 1, event.createdAt().toInstant(),
                new TraceContextSnapshot(event.traceParent(), event.traceState()))
                .dispatcher(metadata.trigger(), metadata.batchSize(), metadata.groupCount(), metadata.inFlight());
        Message<String> message = KafkaRecord.of(event.topic(), event.aggregateId().toString(), event.payload())
                .withHeader(TraceContextSupport.OUTBOX_ATTEMPT, Integer.toString(event.attempts() + 1))
                .addMetadata(TracingMetadata.withCurrent(trace.context()));
        return emitter.sendMessage(message)
                .replaceWith(new PublishOutcome(event, true, null, trace))
                .onFailure().recoverWithItem(failure -> new PublishOutcome(event, false, failure, trace));
    }

    private void finishTraces(List<PublishOutcome> outcomes, BatchResult result, Throwable persistenceFailure) {
        String batchResult = persistenceFailure != null ? "FAILED"
                : result.failureCount() == 0 ? "SUCCEEDED" : "PARTIAL";
        for (PublishOutcome outcome : outcomes) {
            outcome.trace().batchResult(batchResult, result.successCount(), result.failureCount());
            outcome.trace().finish(outcome.failure() == null ? persistenceFailure : outcome.failure());
        }
    }

    private Uni<Void> persistOutcomes(List<PublishOutcome> outcomes) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Tuple> successes = outcomes.stream().filter(PublishOutcome::success)
                .map(outcome -> Tuple.of(now, outcome.event().id())).toList();
        List<Tuple> failures = outcomes.stream().filter(outcome -> !outcome.success())
                .map(outcome -> Tuple.of(outcome.event().id())).toList();
        return pool.withTransaction(connection -> {
            Uni<Void> update = successes.isEmpty()
                    ? Uni.createFrom().voidItem()
                    : connection.preparedQuery(MARK_PUBLISHED).executeBatch(successes).replaceWithVoid();
            if (!failures.isEmpty()) {
                update = update.chain(() -> connection.preparedQuery(MARK_FAILED).executeBatch(failures)
                        .replaceWithVoid());
            }
            return update;
        });
    }

    private static String validChannel(String channel) {
        if (channel == null || !channel.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid PostgreSQL notification channel");
        }
        return channel;
    }

    private static String safeMessage(Throwable failure) {
        String message = failure == null ? "unknown failure" : failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private record DispatchMetadata(String trigger, int batchSize, int groupCount, int inFlight) {
    }

    private record GroupKey(String topic, UUID aggregateId) {
    }

    private record PendingEvent(UUID id, String topic, UUID aggregateId, String payload, int attempts,
            OffsetDateTime createdAt, String traceParent, String traceState) {
    }

    private record PublishOutcome(PendingEvent event, boolean success, Throwable failure,
            TraceContextSupport.OutboxPublishTrace trace) {
    }

    private record BatchResult(long successCount, long failureCount) {
        static BatchResult from(List<PublishOutcome> outcomes) {
            long successes = outcomes.stream().filter(PublishOutcome::success).count();
            return new BatchResult(successes, outcomes.size() - successes);
        }
    }
}
