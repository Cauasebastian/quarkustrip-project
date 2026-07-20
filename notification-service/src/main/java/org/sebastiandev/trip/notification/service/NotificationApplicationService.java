package org.sebastiandev.trip.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.smallrye.mutiny.Uni;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.sebastiandev.trip.contracts.event.EventCodec;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.event.EventPayloads;
import org.sebastiandev.trip.contracts.event.TopicNames;
import org.sebastiandev.trip.contracts.observability.TraceContextSupport;
import org.sebastiandev.trip.notification.domain.Notification;
import org.sebastiandev.trip.notification.domain.ProcessedEvent;
import org.sebastiandev.trip.notification.domain.UserContact;
import org.sebastiandev.trip.notification.repository.NotificationRepository;
import org.sebastiandev.trip.notification.repository.ProcessedEventRepository;
import org.sebastiandev.trip.notification.repository.UserContactRepository;

@ApplicationScoped
public class NotificationApplicationService {
    @Inject ObjectMapper mapper;
    @Inject NotificationRepository notifications;
    @Inject ProcessedEventRepository processed;
    @Inject UserContactRepository contacts;
    @Inject ReactiveMailer mailer;
    @Inject @Channel("notification-events") MutinyEmitter<String> emitter;

    public Uni<Void> updateContact(EventEnvelope event, EventPayloads.UserProfileChanged payload) {
        return process(event, null, () -> processed.findById(event.eventId()).chain(existing -> {
            if (existing != null) {
                Span.current().setAttribute("inbox.duplicate", true);
                return Uni.createFrom().voidItem();
            }
            return contacts.findById(payload.userId()).chain(contact -> {
                UserContact value = contact == null ? new UserContact() : contact;
                value.userId = payload.userId();
                value.subject = payload.subject();
                value.email = payload.email();
                return contact == null ? contacts.persist(value) : contacts.update(value);
            }).chain(() -> mark(event));
        }));
    }

    public Uni<Void> notifyTerminal(EventEnvelope event, EventPayloads.BookingTerminal payload) {
        return process(event, payload.bookingId(), () -> processed.findById(event.eventId()).chain(existing -> {
            if (existing != null) {
                Span.current().setAttribute("inbox.duplicate", true);
                return Uni.createFrom().voidItem();
            }
            return contacts.findById(payload.userId()).chain(contact -> {
                String recipient = contact == null ? payload.userId() + "@local.test" : contact.email;
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                Notification notification = new Notification();
                notification.id = UUID.randomUUID();
                notification.bookingId = payload.bookingId();
                notification.userId = payload.userId();
                notification.channel = "EMAIL";
                notification.type = "BOOKING_" + payload.status();
                notification.status = "PENDING";
                notification.recipient = recipient;
                notification.payloadJson = EventCodec.encode(mapper, event);
                notification.createdAt = now;
                notification.updatedAt = now;
                return notifications.persist(notification).chain(saved -> send(saved, event.eventId()));
            }).chain(() -> mark(event));
        }));
    }

    public Uni<Notification> get(UUID notificationId) {
        return notifications.findById(notificationId);
    }

    private Uni<Void> send(Notification notification, UUID causationId) {
        return TraceContextSupport.traceUni("notification.send", SpanKind.INTERNAL, span -> {
            span.setAttribute("booking.id", notification.bookingId.toString());
            span.setAttribute("event.id", causationId.toString());
            span.setAttribute("notification.channel", notification.channel);
        }, span -> mailer.send(Mail.withText(notification.recipient, "Trip booking " + notification.type,
                        "Booking " + notification.bookingId + " changed to " + notification.type))
                .invoke(() -> {
                    notification.status = "SENT";
                    notification.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
                    span.setAttribute("notification.outcome", "SENT");
                })
                .call(() -> notifications.update(notification))
                .chain(() -> publish(notification, causationId, TopicNames.NOTIFICATION_SENT))
                .onFailure().recoverWithUni(failure -> {
                    notification.status = "FAILED";
                    notification.failureReason = failure.getMessage();
                    notification.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
                    span.setAttribute("notification.outcome", "FAILED");
                    return notifications.update(notification)
                            .chain(() -> publish(notification, causationId, TopicNames.NOTIFICATION_FAILED));
                }));
    }

    private Uni<Void> mark(EventEnvelope event) {
        ProcessedEvent done = new ProcessedEvent();
        done.eventId = event.eventId();
        done.type = event.type();
        done.processedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return processed.persist(done).replaceWithVoid();
    }

    private Uni<Void> publish(Notification notification, UUID cause, String topic) {
        EventPayloads.NotificationOutcome payload = new EventPayloads.NotificationOutcome(notification.id,
                notification.bookingId, notification.userId, notification.channel, notification.status,
                notification.failureReason);
        EventEnvelope event = EventCodec.envelope(mapper, topic, notification.bookingId, cause,
                "notification-service", payload);
        return emitter.sendMessage(KafkaRecord.of(topic, notification.bookingId.toString(),
                EventCodec.encode(mapper, event)));
    }

    private Uni<Void> process(EventEnvelope event, UUID bookingId, Supplier<Uni<Void>> action) {
        Span span = TraceContextSupport.startInboxSpan(event.eventId(), bookingId, event.type());
        return TraceContextSupport.inContext(span, action).onItemOrFailure().invoke((ignored, failure) -> {
            if (failure != null) TraceContextSupport.fail(span, failure);
            span.end();
        });
    }
}
