package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.RoomCodeRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomCodeRegistryRepository extends JpaRepository<RoomCodeRegistry, String> {

  Optional<RoomCodeRegistry> findByRoom_IdAndCurrentTrue(UUID roomId);

  List<RoomCodeRegistry> findAllByRoom_Id(UUID roomId);

  boolean existsByRoom_IdAndUsedInBusinessTrue(UUID roomId);

  @Modifying
  @Query("update RoomCodeRegistry r set r.usedInBusiness = true where r.room.id = :roomId")
  int markRoomCodesUsedInBusiness(@Param("roomId") UUID roomId);

  @Modifying
  @Query("delete from RoomCodeRegistry r where r.room.id = :roomId and r.usedInBusiness = false")
  int deleteUnusedCodes(@Param("roomId") UUID roomId);
}
