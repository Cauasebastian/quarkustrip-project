package org.sebastiandev.trip.payment.repository; import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase; import jakarta.enterprise.context.ApplicationScoped; import java.util.UUID; import org.sebastiandev.trip.payment.messaging.InboxEvent;
@ApplicationScoped public class InboxRepository implements PanacheRepositoryBase<InboxEvent,UUID>{}
