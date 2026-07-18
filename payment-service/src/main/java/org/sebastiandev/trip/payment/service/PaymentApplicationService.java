package org.sebastiandev.trip.payment.service;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.opentelemetry.api.trace.Span;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;
import org.sebastiandev.trip.contracts.event.EventEnvelope;
import org.sebastiandev.trip.contracts.event.EventPayloads;
import org.sebastiandev.trip.contracts.event.TopicNames;
import org.sebastiandev.trip.contracts.observability.TraceContextSupport;
import org.sebastiandev.trip.payment.domain.Payment;
import org.sebastiandev.trip.payment.messaging.InboxEvent;
import org.sebastiandev.trip.payment.messaging.OutboxService;
import org.sebastiandev.trip.payment.provider.PaymentProvider;
import org.sebastiandev.trip.payment.repository.InboxRepository;
import org.sebastiandev.trip.payment.repository.PaymentRepository;

@ApplicationScoped
public class PaymentApplicationService {
    @Inject PaymentRepository payments;
    @Inject InboxRepository inbox;
    @Inject OutboxService outbox;
    @Inject PaymentProvider provider;

    public Uni<Void> charge(EventEnvelope event, EventPayloads.PaymentRequested request) {
        return process(event, () -> payments.find("bookingId", request.bookingId()).firstResult().chain(existing -> {
            if (existing != null) return publish(existing, event.eventId());
            PaymentProvider.Result result = provider.charge(request.paymentMethodRef(), request.amountMinor(),
                    request.currency());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            Payment payment = new Payment();
            payment.id = UUID.randomUUID();
            payment.bookingId = request.bookingId();
            payment.userId = request.userId();
            payment.amountMinor = request.amountMinor();
            payment.currency = request.currency();
            payment.paymentMethodRef = request.paymentMethodRef();
            payment.status = result.successful() ? Payment.Status.SUCCEEDED : Payment.Status.FAILED;
            payment.transactionId = result.transactionId();
            payment.failureReason = result.reason();
            payment.createdAt = now;
            payment.updatedAt = now;
            return payments.persist(payment).chain(() -> publish(payment, event.eventId()));
        }));
    }

    public Uni<Void> refund(EventEnvelope event, EventPayloads.RefundRequested request) {
        return process(event, () -> payments.findById(request.paymentId(), LockModeType.PESSIMISTIC_WRITE)
                .chain(payment -> {
                    if (payment == null || !payment.bookingId.equals(request.bookingId())) {
                        return outbox.enqueue(TopicNames.PAYMENT_REFUND_FAILED, request.bookingId(), event.eventId(),
                                new EventPayloads.PaymentOutcome(request.bookingId(), request.paymentId(),
                                        "REFUND_FAILED", "PAYMENT_NOT_FOUND")).replaceWithVoid();
                    }
                    if (payment.status == Payment.Status.REFUNDED) return publish(payment, event.eventId());
                    PaymentProvider.Result result = provider.refund(payment.transactionId, payment.paymentMethodRef);
                    payment.status = result.successful() ? Payment.Status.REFUNDED : Payment.Status.REFUND_FAILED;
                    payment.failureReason = result.reason();
                    payment.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
                    return publish(payment, event.eventId());
                }));
    }

    public Uni<Payment> getByBookingId(UUID bookingId) {
        return payments.find("bookingId", bookingId).firstResult();
    }

    private Uni<Void> process(EventEnvelope event, Supplier<Uni<Void>> action) {
        Span span = TraceContextSupport.startInboxSpan(event.eventId(), event.correlationId(), event.type());
        return TraceContextSupport.inContext(span, () -> Panache.withTransaction(() ->
                inbox.findById(event.eventId()).chain(existing -> {
                    if (existing != null) {
                        span.setAttribute("inbox.duplicate", true);
                        return Uni.createFrom().voidItem();
                    }
                    return TraceContextSupport.inContext(span, action).chain(() -> {
                        InboxEvent done = new InboxEvent();
                        done.eventId = event.eventId();
                        done.type = event.type();
                        done.processedAt = OffsetDateTime.now(ZoneOffset.UTC);
                        return inbox.persist(done).replaceWithVoid();
                    });
                }))).onItemOrFailure().invoke((ignored, failure) -> {
                    if (failure != null) TraceContextSupport.fail(span, failure);
                    span.end();
                });
    }

    private Uni<Void> publish(Payment payment, UUID cause) {
        String topic = switch (payment.status) {
            case SUCCEEDED -> TopicNames.PAYMENT_SUCCEEDED;
            case FAILED -> TopicNames.PAYMENT_FAILED;
            case REFUNDED -> TopicNames.PAYMENT_REFUNDED;
            case REFUND_FAILED -> TopicNames.PAYMENT_REFUND_FAILED;
            case PENDING -> throw new IllegalStateException("pending payment cannot be published");
        };
        return outbox.enqueue(topic, payment.bookingId, cause,
                new EventPayloads.PaymentOutcome(payment.bookingId, payment.id, payment.status.name(),
                        payment.failureReason)).replaceWithVoid();
    }
}
