package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.RoomAmenity;
import com.uit.dormmanagement.entities.RoomAmenityId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomAmenityRepository extends JpaRepository<RoomAmenity, RoomAmenityId> {
  List<RoomAmenity> findByRoomId(UUID roomId);

  void deleteByRoomId(UUID roomId);
}
