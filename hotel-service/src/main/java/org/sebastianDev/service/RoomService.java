package org.sebastianDev.service;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.sebastianDev.model.Room;
import org.sebastianDev.repository.HotelRepository;
import org.sebastianDev.repository.RoomRepository;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RoomService {

    @Inject
    RoomRepository roomRepository;

    @Inject
    HotelRepository hotelRepository;

    public Uni<List<Room>> getAllRooms() {
        return roomRepository.listAll();
    }

    public Uni<Room> getRoomById(UUID id) {
        return roomRepository.findById(id);
    }

    public Uni<Room> createRoom(Room room) {
        return Panache.withTransaction(() ->
                hotelRepository.findById(room.hotel.id)
                        .onItem().ifNull().failWith(new NotFoundException("Hotel não encontrado (ID: " + room.hotel.id + ")"))
                        .invoke(hotel -> room.hotel = hotel)
                        .call(() -> roomRepository.persist(room))
        ).replaceWith(room);
    }

    public Uni<Room> updateRoom(UUID id, Room roomDetails) {
        return Panache.withTransaction(() ->
                roomRepository.findById(id)
                        .onItem().ifNull().failWith(new NotFoundException("Quarto não encontrado (ID: " + id + ")"))
                        .invoke(room -> {
                            room.roomNumber = roomDetails.roomNumber;
                            room.roomType = roomDetails.roomType;
                            room.price = roomDetails.price;
                            room.isAvailable = roomDetails.isAvailable;
                        })
                        .call(room -> roomRepository.persist(room))
        );
    }

    public Uni<Boolean> deleteRoom(UUID id) {
        return Panache.withTransaction(() ->
                roomRepository.delete("id", id)
                        .map(count -> count > 0)
        );
    }
}
