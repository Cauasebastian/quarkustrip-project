package org.sebastiandev.trip.transport.repository; import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase; import jakarta.enterprise.context.ApplicationScoped; import java.util.UUID; import org.sebastiandev.trip.transport.domain.TransportReservation;
@ApplicationScoped public class TransportReservationRepository implements PanacheRepositoryBase<TransportReservation,UUID>{}
