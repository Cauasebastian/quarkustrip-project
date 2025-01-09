package org.sebastianDev.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
        return hotelRepository.findById(room.hotel.id)
                .onItem().ifNotNull().transformToUni(hotel -> {
                    room.hotel = hotel;
                    return roomRepository.persist(room).replaceWith(room);
                })
                .onItem().ifNull().failWith(new IllegalArgumentException("Hotel não encontrado para o ID: " + room.hotel.id));
    }

    public Uni<Room> updateRoom(UUID id, Room roomDetails) {
        return roomRepository.findById(id).onItem().ifNotNull().transformToUni(room -> {
            room.roomNumber = roomDetails.roomNumber;
            room.roomType = roomDetails.roomType;
            room.price = roomDetails.price;
            room.isAvailable = roomDetails.isAvailable;
            return roomRepository.persist(room).replaceWith(room);
        });
    }

    public Uni<Boolean> deleteRoom(UUID id) {
        return roomRepository.delete("id", id).map(count -> count > 0);
    }
}
