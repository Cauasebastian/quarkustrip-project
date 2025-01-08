package org.sebastianDev.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.sebastianDev.model.Room;
import org.sebastianDev.repository.RoomRepository;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RoomService {

    @Inject
    RoomRepository roomRepository;

    public List<Room> getAllRooms() {
        return roomRepository.listAll();
    }

    public Room getRoomById(UUID id) {
        return roomRepository.findById(id);
    }

    public Room createRoom(Room room) {
        roomRepository.persist(room);
        return room;
    }

    public Room updateRoom(UUID id, Room roomDetails) {
        Room room = roomRepository.findById(id);
        if (room != null) {
            room.roomNumber = roomDetails.roomNumber;
            room.roomType = roomDetails.roomType;
            room.price = roomDetails.price;
            room.isAvailable = roomDetails.isAvailable;
            roomRepository.persist(room);
        }
        return room;
    }

    public boolean deleteRoom(UUID id) {
        return roomRepository.deleteById(id);
    }
}
