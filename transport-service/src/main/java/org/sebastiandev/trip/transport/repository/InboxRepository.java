package org.sebastiandev.trip.transport.repository; import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase; import jakarta.enterprise.context.ApplicationScoped; import java.util.UUID; import org.sebastiandev.trip.transport.messaging.InboxEvent;
@ApplicationScoped public class InboxRepository implements PanacheRepositoryBase<InboxEvent,UUID>{}
