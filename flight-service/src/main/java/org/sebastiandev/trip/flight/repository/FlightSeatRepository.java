package org.sebastiandev.trip.flight.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import org.sebastiandev.trip.flight.domain.FlightSeat;

@ApplicationScoped public class FlightSeatRepository implements PanacheRepositoryBase<FlightSeat, UUID> {}
