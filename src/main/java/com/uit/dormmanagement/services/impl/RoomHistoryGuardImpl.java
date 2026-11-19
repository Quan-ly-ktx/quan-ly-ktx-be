package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.MaintenanceTicketRepository;
import com.uit.dormmanagement.repositories.RoomCodeRegistryRepository;
import com.uit.dormmanagement.services.RoomHistoryGuard;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomHistoryGuardImpl implements RoomHistoryGuard {

  private final RoomCodeRegistryRepository roomCodeRegistryRepository;
  private final ContractRepository contractRepository;
  private final MaintenanceTicketRepository maintenanceTicketRepository;

  @Override
  @Transactional(readOnly = true)
  public boolean hasProtectedHistory(UUID roomId, Collection<UUID> bedIds) {
    if (roomCodeRegistryRepository.existsByRoom_IdAndUsedInBusinessTrue(roomId)) {
      return true;
    }
    if (contractRepository.existsByBed_Room_Id(roomId)) {
      return true;
    }
    if (bedIds != null && !bedIds.isEmpty() && contractRepository.existsByBed_IdIn(bedIds)) {
      return true;
    }
    if (maintenanceTicketRepository.existsByRoomId(roomId)) {
      return true;
    }
    if (bedIds != null && !bedIds.isEmpty() && maintenanceTicketRepository.existsByBed_IdIn(bedIds)) {
      return true;
    }
    return false;
  }
}
