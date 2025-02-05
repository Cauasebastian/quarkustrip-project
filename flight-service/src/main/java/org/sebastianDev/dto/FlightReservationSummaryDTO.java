package org.sebastianDev.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public class FlightReservationSummaryDTO {

    public UUID id;
    public String seatNumber;
    public UUID bookingId;
    public UUID userId;
    public String status;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;

    public FlightReservationSummaryDTO(UUID id, String seatNumber, UUID bookingId, UUID userId, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.seatNumber = seatNumber;
        this.bookingId = bookingId;
        this.userId = userId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
