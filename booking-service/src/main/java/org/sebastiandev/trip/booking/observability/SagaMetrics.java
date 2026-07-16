package org.sebastiandev.trip.booking.observability;
import io.micrometer.core.instrument.MeterRegistry; import io.micrometer.core.instrument.Timer; import jakarta.enterprise.context.ApplicationScoped; import jakarta.inject.Inject; import java.time.*; import org.sebastiandev.trip.booking.domain.Booking;
@ApplicationScoped public class SagaMetrics{
 @Inject MeterRegistry registry;
 public void transition(String state){registry.counter("trip.saga.transitions","state",state).increment();}
 public void compensation(){registry.counter("trip.saga.compensations").increment();}
 public void manualReview(){registry.counter("trip.saga.manual_review").increment();}
 public void terminal(Booking booking){Timer.builder("trip.saga.duration").tag("status",booking.status.name()).register(registry).record(Duration.between(booking.createdAt,OffsetDateTime.now(ZoneOffset.UTC)));}
}
