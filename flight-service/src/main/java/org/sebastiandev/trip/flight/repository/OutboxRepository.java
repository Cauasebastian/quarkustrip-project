package org.sebastiandev.trip.flight.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import org.sebastiandev.trip.flight.messaging.OutboxEvent;

@ApplicationScoped public class OutboxRepository implements PanacheRepositoryBase<OutboxEvent, UUID> {}
