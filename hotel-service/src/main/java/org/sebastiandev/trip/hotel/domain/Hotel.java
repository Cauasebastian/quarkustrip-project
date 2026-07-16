package org.sebastiandev.trip.hotel.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "hotels")
public class Hotel {
    @Id public UUID id;
    @Column(nullable = false) public String name;
    @Column(nullable = false) public String address;
    @Column(nullable = false) public String city;
    @Column(nullable = false, length = 2) public String country;
    @Column(nullable = false) public int rating;
    @Version public long version;
}
