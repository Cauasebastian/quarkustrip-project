package org.sebastianDev.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class FlightDTO {
    public UUID id;
    public String flightNumber;
    public String origin;
    public String destination;
    public OffsetDateTime departureTime;
    public OffsetDateTime arrivalTime;
    public int totalSeats;
    public int availableSeats;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;

    public List<SeatInfoDTO> seats;
}