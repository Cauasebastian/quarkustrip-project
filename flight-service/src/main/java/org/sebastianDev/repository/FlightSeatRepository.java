package org.sebastianDev.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.sebastianDev.model.FlightSeat;

@ApplicationScoped
public class FlightSeatRepository implements PanacheRepository<FlightSeat> {
}