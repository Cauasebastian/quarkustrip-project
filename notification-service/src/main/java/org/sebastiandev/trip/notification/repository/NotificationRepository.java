package org.sebastiandev.trip.notification.repository; import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepositoryBase; import jakarta.enterprise.context.ApplicationScoped; import java.util.UUID; import org.sebastiandev.trip.notification.domain.Notification;
@ApplicationScoped public class NotificationRepository implements ReactivePanacheMongoRepositoryBase<Notification,UUID>{}
