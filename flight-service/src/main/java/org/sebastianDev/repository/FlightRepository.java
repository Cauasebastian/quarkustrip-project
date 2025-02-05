package org.sebastianDev.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.sebastianDev.model.Flight;

@ApplicationScoped
public class FlightRepository implements PanacheRepository<Flight> {
}