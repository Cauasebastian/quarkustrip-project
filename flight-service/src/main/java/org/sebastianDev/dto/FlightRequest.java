package org.sebastianDev.dto;

import java.time.OffsetDateTime;

public class FlightRequest {
    public String flightNumber;
    public String origin;
    public String destination;
    public OffsetDateTime departureTime;
    public OffsetDateTime arrivalTime;
    public int totalSeats;
}