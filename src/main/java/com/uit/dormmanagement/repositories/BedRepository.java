package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.enums.BedStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BedRepository extends JpaRepository<Bed, UUID> {
  Optional<Bed> findBySlotKey(String slotKey);

  boolean existsBySlotKey(String slotKey);

  List<Bed> findByRoomId(UUID roomId);

  List<Bed> findByRoomIdAndStatus(UUID roomId, BedStatus status);

  long countByRoomIdAndStatus(UUID roomId, BedStatus status);

  List<Bed> findByStatus(BedStatus status);

  List<Bed> findByRoomIdOrderByBedCodeAsc(UUID roomId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM Bed b WHERE b.room.id = :roomId ORDER BY b.id ASC")
  List<Bed> findByRoomIdForUpdateOrderById(@Param("roomId") UUID roomId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT b FROM Bed b WHERE b.id = :id")
  Optional<Bed> findByIdForUpdate(@Param("id") UUID id);
}
