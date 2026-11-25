package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.MaintenanceTicketRepository;
import com.uit.dormmanagement.repositories.RoomCodeRegistryRepository;
import com.uit.dormmanagement.services.impl.RoomHistoryGuardImpl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomHistoryGuardTest {

  @Mock private RoomCodeRegistryRepository roomCodeRegistryRepository;
  @Mock private ContractRepository contractRepository;
  @Mock private MaintenanceTicketRepository maintenanceTicketRepository;

  @InjectMocks private RoomHistoryGuardImpl roomHistoryGuard;

  @Test
  @DisplayName("Should return true when room code was used in business")
  void shouldReturnTrueWhenCodeUsedInBusiness() {
    UUID roomId = UUID.randomUUID();
    when(roomCodeRegistryRepository.existsByRoom_IdAndUsedInBusinessTrue(roomId)).thenReturn(true);

    assertThat(roomHistoryGuard.hasProtectedHistory(roomId, List.of())).isTrue();
  }

  @Test
  @DisplayName("Should return true when contract exists for room")
  void shouldReturnTrueWhenContractExistsForRoom() {
    UUID roomId = UUID.randomUUID();
    when(roomCodeRegistryRepository.existsByRoom_IdAndUsedInBusinessTrue(roomId)).thenReturn(false);
    when(contractRepository.existsByBed_Room_Id(roomId)).thenReturn(true);

    assertThat(roomHistoryGuard.hasProtectedHistory(roomId, List.of())).isTrue();
  }

  @Test
  @DisplayName("Should return true when maintenance ticket exists for bed")
  void shouldReturnTrueWhenMaintenanceTicketExistsForBed() {
    UUID roomId = UUID.randomUUID();
    UUID bedId = UUID.randomUUID();
    when(roomCodeRegistryRepository.existsByRoom_IdAndUsedInBusinessTrue(roomId)).thenReturn(false);
    when(contractRepository.existsByBed_Room_Id(roomId)).thenReturn(false);
    when(contractRepository.existsByBed_IdIn(List.of(bedId))).thenReturn(false);
    when(maintenanceTicketRepository.existsByRoomId(roomId)).thenReturn(false);
    when(maintenanceTicketRepository.existsByBed_IdIn(List.of(bedId))).thenReturn(true);

    assertThat(roomHistoryGuard.hasProtectedHistory(roomId, List.of(bedId))).isTrue();
  }

  @Test
  @DisplayName("Should return false when no history exists")
  void shouldReturnFalseWhenNoHistory() {
    UUID roomId = UUID.randomUUID();
    UUID bedId = UUID.randomUUID();
    when(roomCodeRegistryRepository.existsByRoom_IdAndUsedInBusinessTrue(roomId)).thenReturn(false);
    when(contractRepository.existsByBed_Room_Id(roomId)).thenReturn(false);
    when(contractRepository.existsByBed_IdIn(List.of(bedId))).thenReturn(false);
    when(maintenanceTicketRepository.existsByRoomId(roomId)).thenReturn(false);
    when(maintenanceTicketRepository.existsByBed_IdIn(List.of(bedId))).thenReturn(false);

    assertThat(roomHistoryGuard.hasProtectedHistory(roomId, List.of(bedId))).isFalse();
  }
}
