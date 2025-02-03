package org.sebastianDev.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.sebastianDev.model.FlightReservation;

@ApplicationScoped
public class FlightReservationRepository implements PanacheRepository<FlightReservation> {
}