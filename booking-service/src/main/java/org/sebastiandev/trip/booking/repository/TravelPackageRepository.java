package org.sebastiandev.trip.booking.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import org.sebastiandev.trip.booking.domain.TravelPackage;

@ApplicationScoped
public class TravelPackageRepository implements PanacheRepositoryBase<TravelPackage, UUID> {
}
