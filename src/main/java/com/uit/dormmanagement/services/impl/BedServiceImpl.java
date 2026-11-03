package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.request.room.UpdateBedStatusRequest;
import com.uit.dormmanagement.dtos.response.room.BedResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.services.BedService;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BedServiceImpl implements BedService {

  private final BedRepository bedRepository;
  private final ContractRepository contractRepository;

  @Override
  @Transactional
  public BedResponse updateBedStatus(UUID bedId, UpdateBedStatusRequest request) {
    Bed bed =
        bedRepository
            .findById(bedId)
            .orElseThrow(() -> new BaseAppException(RoomErrorCode.BED_NOT_FOUND));

    BedStatus newStatus = request.status();

    if (newStatus == BedStatus.MAINTENANCE) {
      if (bed.getStatus() == BedStatus.OCCUPIED) {
        throw new BaseAppException(RoomErrorCode.CANNOT_MAINTAIN_OCCUPIED_BED);
      }
      boolean hasActiveContract =
          contractRepository.existsByBedIdAndStatusInAndDeletedAtIsNull(
              bedId, Set.of(ContractStatus.ACTIVE, ContractStatus.EXPIRING_SOON));
      if (hasActiveContract) {
        throw new BaseAppException(RoomErrorCode.CANNOT_MAINTAIN_OCCUPIED_BED);
      }
      bed.setStatus(BedStatus.MAINTENANCE);
    } else if (newStatus == BedStatus.AVAILABLE) {
      if (bed.getStatus() == BedStatus.OCCUPIED) {
        throw new BaseAppException(RoomErrorCode.CANNOT_MAINTAIN_OCCUPIED_BED);
      }
      bed.setStatus(BedStatus.AVAILABLE);
    } else {
      throw new BaseAppException(RoomErrorCode.BED_NOT_AVAILABLE);
    }

    Bed savedBed = bedRepository.save(bed);
    Room room = savedBed.getRoom();

    return new BedResponse(
        savedBed.getId(),
        room != null ? room.getId() : null,
        room != null ? room.getRoomNumber() : null,
        savedBed.getBedCode(),
        savedBed.getSlotKey(),
        savedBed.getPrice(),
        savedBed.getStatus());
  }
}
