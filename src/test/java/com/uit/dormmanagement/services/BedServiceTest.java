package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.request.room.UpdateBedStatusRequest;
import com.uit.dormmanagement.dtos.response.room.BedResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.services.impl.BedServiceImpl;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BedServiceTest {

  @Mock private BedRepository bedRepository;
  @Mock private ContractRepository contractRepository;

  @InjectMocks private BedServiceImpl bedService;

  private Room testRoom;
  private Bed testBed;
  private UUID bedId;

  @BeforeEach
  void setUp() {
    bedId = UUID.randomUUID();
    testRoom =
        Room.builder()
            .id(UUID.randomUUID())
            .roomNumber("P.302")
            .floor(3)
            .roomType(RoomType.FOUR_BEDS)
            .genderPolicy(GenderPolicy.MALE)
            .totalBeds(4)
            .basePrice(new BigDecimal("1500000.00"))
            .build();

    testBed =
        Bed.builder()
            .id(bedId)
            .room(testRoom)
            .bedCode("G01")
            .slotKey("P.302-G01")
            .price(new BigDecimal("1500000.00"))
            .status(BedStatus.AVAILABLE)
            .build();
  }

  @Test
  @DisplayName(
      "updateBedStatus should successfully set status to MAINTENANCE when bed is AVAILABLE")
  void updateBedStatus_whenAvailable_shouldSetMaintenance() {
    when(bedRepository.findById(bedId)).thenReturn(Optional.of(testBed));
    when(contractRepository.existsByBedIdAndStatusInAndDeletedAtIsNull(eq(bedId), any()))
        .thenReturn(false);
    when(bedRepository.save(any(Bed.class))).thenAnswer(invocation -> invocation.getArgument(0));

    UpdateBedStatusRequest request = new UpdateBedStatusRequest(BedStatus.MAINTENANCE);
    BedResponse response = bedService.updateBedStatus(bedId, request);

    assertThat(response.status()).isEqualTo(BedStatus.MAINTENANCE);
    assertThat(testBed.getStatus()).isEqualTo(BedStatus.MAINTENANCE);
    verify(bedRepository).save(testBed);
  }

  @Test
  @DisplayName("updateBedStatus should throw CANNOT_MAINTAIN_OCCUPIED_BED when bed is OCCUPIED")
  void updateBedStatus_whenOccupied_shouldThrow() {
    testBed.setStatus(BedStatus.OCCUPIED);
    when(bedRepository.findById(bedId)).thenReturn(Optional.of(testBed));

    UpdateBedStatusRequest request = new UpdateBedStatusRequest(BedStatus.MAINTENANCE);

    assertThatThrownBy(() -> bedService.updateBedStatus(bedId, request))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.CANNOT_MAINTAIN_OCCUPIED_BED));
  }

  @Test
  @DisplayName(
      "updateBedStatus should throw CANNOT_MAINTAIN_OCCUPIED_BED when active contract exists")
  void updateBedStatus_whenActiveContractExists_shouldThrow() {
    when(bedRepository.findById(bedId)).thenReturn(Optional.of(testBed));
    when(contractRepository.existsByBedIdAndStatusInAndDeletedAtIsNull(eq(bedId), any()))
        .thenReturn(true);

    UpdateBedStatusRequest request = new UpdateBedStatusRequest(BedStatus.MAINTENANCE);

    assertThatThrownBy(() -> bedService.updateBedStatus(bedId, request))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.CANNOT_MAINTAIN_OCCUPIED_BED));
  }

  @Test
  @DisplayName(
      "updateBedStatus should successfully set status to AVAILABLE when bed is MAINTENANCE")
  void updateBedStatus_whenMaintenance_shouldSetAvailable() {
    testBed.setStatus(BedStatus.MAINTENANCE);
    when(bedRepository.findById(bedId)).thenReturn(Optional.of(testBed));
    when(bedRepository.save(any(Bed.class))).thenAnswer(invocation -> invocation.getArgument(0));

    UpdateBedStatusRequest request = new UpdateBedStatusRequest(BedStatus.AVAILABLE);
    BedResponse response = bedService.updateBedStatus(bedId, request);

    assertThat(response.status()).isEqualTo(BedStatus.AVAILABLE);
    assertThat(testBed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
    verify(bedRepository).save(testBed);
  }

  @Test
  @DisplayName("updateBedStatus should throw BED_NOT_FOUND when bed does not exist")
  void updateBedStatus_whenBedNotFound_shouldThrow() {
    when(bedRepository.findById(bedId)).thenReturn(Optional.empty());

    UpdateBedStatusRequest request = new UpdateBedStatusRequest(BedStatus.MAINTENANCE);

    assertThatThrownBy(() -> bedService.updateBedStatus(bedId, request))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.BED_NOT_FOUND));
  }
}
