package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.RoomStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
  Optional<Room> findByRoomNumber(String roomNumber);

  boolean existsByRoomNumber(String roomNumber);

  List<Room> findByFloor(int floor);

  List<Room> findByGenderPolicy(GenderPolicy genderPolicy);

  List<Room> findByStatus(RoomStatus status);

  List<Room> findByFloorAndGenderPolicy(int floor, GenderPolicy genderPolicy);
}
