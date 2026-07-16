package org.sebastiandev.trip.transport.domain;
import jakarta.persistence.*; import java.util.UUID;
@Entity @Table(name="transport_offers") public class TransportOffer {
 @Id public UUID id; @Column(name="transport_type",nullable=false) public String transportType; @Column(name="provider_name",nullable=false) public String providerName;
 @Column(name="vehicle_details",nullable=false,columnDefinition="jsonb") public String vehicleDetailsJson; @Column(name="price_minor",nullable=false) public long priceMinor;
 @Column(nullable=false,length=3) public String currency; @Column(nullable=false) public boolean active; @Version public long version;
}
