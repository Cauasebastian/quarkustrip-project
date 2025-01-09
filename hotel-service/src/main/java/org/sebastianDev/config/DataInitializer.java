package org.sebastianDev.config;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.sebastianDev.model.Hotel;
import org.sebastianDev.model.HotelReservation;
import org.sebastianDev.model.Room;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class DataInitializer {

    private final String baseUrl = "http://localhost:8080";

    private final Client client = ClientBuilder.newClient();

    @PostConstruct
    public void initializeData() {
        System.out.println("Inicializando dados...");

        // Criar hotel
        Hotel hotel = new Hotel();
        hotel.name = "Hotel Example";
        hotel.address = "123 Example Street";
        hotel.city = "Example City";
        hotel.country = "Example Country";
        hotel.rating = 5;

        Response hotelResponse = client.target(baseUrl + "/hotels")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(hotel, MediaType.APPLICATION_JSON));
        if (hotelResponse.getStatus() != 201) {
            throw new RuntimeException("Falha ao criar hotel");
        }
        Hotel createdHotel = hotelResponse.readEntity(Hotel.class);
        System.out.println("Hotel criado: " + createdHotel.name + " (ID: " + createdHotel.id + ")");

        // Criar room no hotel
        Room room = new Room();
        room.hotel = createdHotel;
        room.roomNumber = "101";
        room.roomType = "Suite";
        room.price = BigDecimal.valueOf(200.00);
        room.isAvailable = true;

        Response roomResponse = client.target(baseUrl + "/rooms")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(room, MediaType.APPLICATION_JSON));
        if (roomResponse.getStatus() != 201) {
            throw new RuntimeException("Falha ao criar room");
        }
        Room createdRoom = roomResponse.readEntity(Room.class);
        System.out.println("Room criado: " + createdRoom.roomNumber + " (ID: " + createdRoom.id + ")");

        // Criar reserva no hotel
        HotelReservation reservation = new HotelReservation();
        reservation.room = createdRoom;
        reservation.bookingId = UUID.randomUUID(); // Simulação de ID de reserva externa
        reservation.userId = UUID.randomUUID(); // Simulação de ID de usuário
        reservation.checkInDate = LocalDate.now().plusDays(1);
        reservation.checkOutDate = LocalDate.now().plusDays(3);
        reservation.status = "Booked";

        Response reservationResponse = client.target(baseUrl + "/reservations")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(reservation, MediaType.APPLICATION_JSON));
        if (reservationResponse.getStatus() != 201) {
            throw new RuntimeException("Falha ao criar reservation");
        }
        HotelReservation createdReservation = reservationResponse.readEntity(HotelReservation.class);
        System.out.println("Reserva criada: " + createdReservation.id);
    }
}
