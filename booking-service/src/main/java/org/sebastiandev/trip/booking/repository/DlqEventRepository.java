package org.sebastiandev.trip.booking.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import org.sebastiandev.trip.booking.messaging.DlqEvent;

@ApplicationScoped
public class DlqEventRepository implements PanacheRepositoryBase<DlqEvent, String> {
}
