package org.sebastianDev.dto;

import java.util.UUID;

public class ReservationRequest {
    public UUID flightId;
    public String seatNumber;
    public UUID userId;
    public UUID bookingId;  // Novo campo: bookingId
}
