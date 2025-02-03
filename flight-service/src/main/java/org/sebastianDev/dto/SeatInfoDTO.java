package org.sebastianDev.dto;

public class SeatInfoDTO {
    public String seatNumber;
    public String status;

    public SeatInfoDTO(String seatNumber, String status) {
        this.seatNumber = seatNumber;
        this.status = status;
    }
}