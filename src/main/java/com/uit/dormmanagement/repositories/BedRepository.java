package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.enums.BedStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BedRepository extends JpaRepository<Bed, UUID> {
  Optional<Bed> findBySlotKey(String slotKey);

  boolean existsBySlotKey(String slotKey);

  List<Bed> findByRoomId(UUID roomId);

  List<Bed> findByRoomIdAndStatus(UUID roomId, BedStatus status);

  List<Bed> findByStatus(BedStatus status);

  List<Bed> findByRoomIdOrderByBedCodeAsc(UUID roomId);
}
