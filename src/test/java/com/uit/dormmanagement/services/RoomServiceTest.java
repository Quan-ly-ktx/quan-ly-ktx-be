package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.request.room.RoomAmenityRequest;
import com.uit.dormmanagement.dtos.request.room.UpdateRoomRequest;
import com.uit.dormmanagement.dtos.response.room.ArchivedRoomSummaryResponse;
import com.uit.dormmanagement.dtos.response.room.RoomDetailResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.RoomCodeRegistry;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.repositories.AmenityRepository;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.RoomAmenityRepository;
import com.uit.dormmanagement.repositories.RoomCodeRegistryRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.services.impl.RoomServiceImpl;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

  @Mock private RoomRepository roomRepository;
  @Mock private BedRepository bedRepository;
  @Mock private RoomAmenityRepository roomAmenityRepository;
  @Mock private RoomCodeRegistryRepository roomCodeRegistryRepository;
  @Mock private AmenityRepository amenityRepository;
  @Mock private ContractRepository contractRepository;
  @Mock private RoomHistoryGuard roomHistoryGuard;

  @InjectMocks private RoomServiceImpl roomService;

  private UUID roomId;
  private Room testRoom;

  @BeforeEach
  void setUp() {
    roomId = UUID.randomUUID();
    testRoom =
        Room.builder()
            .id(roomId)
            .roomNumber("P.302")
            .floor(3)
            .genderPolicy(GenderPolicy.MALE)
            .totalBeds((short) 4)
            .basePrice(new BigDecimal("1500000.00"))
            .build();
  }

  @Test
  @DisplayName("createRoom should create room, current code, and N beds with derived counts")
  void createRoom_success() {
    CreateRoomRequest request =
        new CreateRoomRequest(
            "P.302",
            3,
            RoomType.FOUR_BEDS,
            GenderPolicy.MALE,
            new BigDecimal("1500000.00"),
            List.of());

    when(roomRepository.existsByRoomNumber("P.302")).thenReturn(false);
    when(roomRepository.saveAndFlush(any(Room.class)))
        .thenAnswer(
            inv -> {
              Room r = inv.getArgument(0);
              r.setId(roomId);
              return r;
            });

    RoomDetailResponse response = roomService.createRoom(request);

    assertThat(response.roomNumber()).isEqualTo("P.302");
    assertThat(response.totalBeds()).isEqualTo(4);
    assertThat(response.occupiedBeds()).isEqualTo(0);
    assertThat(response.availableBeds()).isEqualTo(4);
    assertThat(response.maintenanceBeds()).isEqualTo(0);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Bed>> bedsCaptor = ArgumentCaptor.forClass(List.class);
    verify(bedRepository).saveAllAndFlush(bedsCaptor.capture());

    List<Bed> generatedBeds = bedsCaptor.getValue();
    assertThat(generatedBeds).hasSize(4);
    assertThat(generatedBeds.get(0).getBedCode()).isEqualTo("G01");
    assertThat(generatedBeds.get(0).getSlotKey()).isEqualTo("P.302-G01");
    assertThat(generatedBeds.get(0).getRoom().getBasePrice())
        .isEqualByComparingTo(new BigDecimal("1500000.00"));
    assertThat(generatedBeds.get(0).getStatus()).isEqualTo(BedStatus.AVAILABLE);
    assertThat(generatedBeds.get(3).getBedCode()).isEqualTo("G04");
  }

  @Test
  @DisplayName("createRoom should fail and throw AMENITY_NOT_FOUND when amenity does not exist")
  void createRoom_whenAmenityNotFound_shouldThrow() {
    UUID invalidAmenityId = UUID.randomUUID();
    CreateRoomRequest request =
        new CreateRoomRequest(
            "P.302",
            3,
            RoomType.FOUR_BEDS,
            GenderPolicy.MALE,
            new BigDecimal("1500000.00"),
            List.of(new RoomAmenityRequest(invalidAmenityId, 2)));

    when(roomRepository.existsByRoomNumber("P.302")).thenReturn(false);
    when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
    when(amenityRepository.findAllById(any())).thenReturn(List.of());

    assertThatThrownBy(() -> roomService.createRoom(request))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.AMENITY_NOT_FOUND));
  }

  @Test
  @DisplayName(
      "updateRoom should allow genderPolicy change when occupiedBeds is 0 and no active contract")
  void updateRoom_whenNoOccupants_shouldAllowGenderPolicyChange() {
    Bed bed1 =
        Bed.builder().id(UUID.randomUUID()).room(testRoom).status(BedStatus.AVAILABLE).build();
    when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(testRoom));
    when(bedRepository.findByRoomIdForUpdateOrderById(roomId)).thenReturn(List.of(bed1));
    when(contractRepository.existsByBed_Room_IdAndStatusIn(eq(roomId), any())).thenReturn(false);
    when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
    when(bedRepository.findByRoomIdOrderByBedCodeAsc(roomId)).thenReturn(List.of(bed1));

    UpdateRoomRequest request =
        new UpdateRoomRequest(GenderPolicy.FEMALE, new BigDecimal("1700000.00"), List.of());

    RoomDetailResponse response = roomService.updateRoom(roomId, request);

    assertThat(response.genderPolicy()).isEqualTo(GenderPolicy.FEMALE);
    assertThat(response.basePrice()).isEqualByComparingTo(new BigDecimal("1700000.00"));
  }

  @Test
  @DisplayName("updateRoom should reject genderPolicy change when occupiedBeds > 0")
  void updateRoom_whenOccupied_shouldRejectGenderPolicyChange() {
    Bed occupiedBed =
        Bed.builder().id(UUID.randomUUID()).room(testRoom).status(BedStatus.OCCUPIED).build();
    when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(testRoom));
    when(bedRepository.findByRoomIdForUpdateOrderById(roomId)).thenReturn(List.of(occupiedBed));

    UpdateRoomRequest request =
        new UpdateRoomRequest(GenderPolicy.FEMALE, new BigDecimal("1500000.00"), List.of());

    assertThatThrownBy(() -> roomService.updateRoom(roomId, request))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.ROOM_OCCUPIED_OR_CONTRACT_ACTIVE));
  }

  @Test
  @DisplayName("updateRoom should reject rename when room has active contract")
  void updateRoom_whenActiveContract_shouldRejectRename() {
    Bed availableBed =
        Bed.builder().id(UUID.randomUUID()).room(testRoom).status(BedStatus.AVAILABLE).build();
    when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(testRoom));
    when(bedRepository.findByRoomIdForUpdateOrderById(roomId)).thenReturn(List.of(availableBed));
    when(contractRepository.existsByBed_Room_IdAndStatusIn(eq(roomId), any())).thenReturn(true);

    UpdateRoomRequest request =
        new UpdateRoomRequest("P.303", GenderPolicy.MALE, new BigDecimal("1500000.00"), List.of());

    assertThatThrownBy(() -> roomService.updateRoom(roomId, request))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.ROOM_OCCUPIED_OR_CONTRACT_ACTIVE));
  }

  @Test
  @DisplayName("updateRoom rename should update registry and bed slot keys")
  void updateRoom_renameShouldUpdateRegistryAndBedSlotKeys() {
    Bed bed =
        Bed.builder()
            .id(UUID.randomUUID())
            .room(testRoom)
            .bedCode("G01")
            .slotKey("P.302-G01")
            .status(BedStatus.AVAILABLE)
            .build();
    com.uit.dormmanagement.entities.RoomCodeRegistry oldCode =
        com.uit.dormmanagement.entities.RoomCodeRegistry.builder()
            .code("P.302")
            .room(testRoom)
            .current(true)
            .usedInBusiness(true)
            .build();

    when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(testRoom));
    when(bedRepository.findByRoomIdForUpdateOrderById(roomId)).thenReturn(List.of(bed));
    when(contractRepository.existsByBed_Room_IdAndStatusIn(eq(roomId), any())).thenReturn(false);
    when(roomCodeRegistryRepository.findById("P.303")).thenReturn(Optional.empty());
    when(roomCodeRegistryRepository.findByRoom_IdAndCurrentTrue(roomId))
        .thenReturn(Optional.of(oldCode));
    when(bedRepository.findByRoomIdOrderByBedCodeAsc(roomId)).thenReturn(List.of(bed));
    when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
    when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

    roomService.updateRoom(
        roomId, new UpdateRoomRequest("P.303", GenderPolicy.MALE, new BigDecimal("1500000"), null));

    assertThat(testRoom.getRoomNumber()).isEqualTo("P.303");
    assertThat(oldCode.isCurrent()).isFalse();
    assertThat(oldCode.isUsedInBusiness()).isTrue();
    assertThat(bed.getSlotKey()).isEqualTo("P.303-G01");
    verify(roomCodeRegistryRepository).saveAndFlush(oldCode);
    verify(bedRepository).saveAllAndFlush(List.of(bed));
  }

  @Test
  @DisplayName("deleteRoom should archive room when vacant and no active contracts")
  void deleteRoom_whenVacant_shouldArchive() {
    Bed bed =
        Bed.builder().id(UUID.randomUUID()).room(testRoom).status(BedStatus.AVAILABLE).build();
    when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(testRoom));
    when(bedRepository.findByRoomIdForUpdateOrderById(roomId)).thenReturn(List.of(bed));
    when(contractRepository.existsByBed_Room_IdAndStatusIn(eq(roomId), any())).thenReturn(false);

    roomService.deleteRoom(roomId);

    assertThat(testRoom.getArchivedAt()).isNotNull();
    verify(roomRepository).save(testRoom);
  }

  @Test
  @DisplayName("restoreRoom should restore archived room and return details")
  void restoreRoom_whenArchived_shouldRestore() {
    testRoom.setArchivedAt(Instant.now().minusSeconds(3600));
    when(roomRepository.findByIdUnfilteredForUpdate(roomId)).thenReturn(Optional.of(testRoom));
    when(roomRepository.existsByRoomNumber("P.302")).thenReturn(false);
    when(roomRepository.restoreArchivedRoom(eq(roomId), any())).thenReturn(1);
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));
    when(bedRepository.findByRoomIdOrderByBedCodeAsc(roomId)).thenReturn(List.of());

    RoomDetailResponse response = roomService.restoreRoom(roomId);

    assertThat(response.id()).isEqualTo(roomId);
    assertThat(response.roomNumber()).isEqualTo("P.302");
    verify(roomRepository).restoreArchivedRoom(eq(roomId), any());
  }

  @Test
  @DisplayName("deleteIncorrectRoom should reject when room has protected history")
  void deleteIncorrectRoom_whenProtectedHistory_shouldThrow() {
    Bed bed =
        Bed.builder().id(UUID.randomUUID()).room(testRoom).status(BedStatus.AVAILABLE).build();
    when(roomRepository.findByIdUnfilteredForUpdate(roomId)).thenReturn(Optional.of(testRoom));
    when(bedRepository.findByRoomIdForUpdateOrderById(roomId)).thenReturn(List.of(bed));
    when(roomHistoryGuard.hasProtectedHistory(eq(roomId), any())).thenReturn(true);

    assertThatThrownBy(() -> roomService.deleteIncorrectRoom(roomId))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.ROOM_HAS_HISTORY));
  }

  @Test
  @DisplayName("deleteIncorrectRoom should delete unused room cleanly")
  void deleteIncorrectRoom_whenUnused_shouldDeleteCleanly() {
    Bed bed =
        Bed.builder().id(UUID.randomUUID()).room(testRoom).status(BedStatus.AVAILABLE).build();
    when(roomRepository.findByIdUnfilteredForUpdate(roomId)).thenReturn(Optional.of(testRoom));
    when(bedRepository.findByRoomIdForUpdateOrderById(roomId)).thenReturn(List.of(bed));
    when(roomHistoryGuard.hasProtectedHistory(eq(roomId), any())).thenReturn(false);

    roomService.deleteIncorrectRoom(roomId);

    verify(roomAmenityRepository).deleteByRoomId(roomId);
    verify(bedRepository).deleteAll(List.of(bed));
    verify(roomCodeRegistryRepository).deleteUnusedCodes(roomId);
    verify(roomRepository).delete(testRoom);
  }

  @Test
  @DisplayName("getArchivedRooms should query and map archived room summaries")
  void getArchivedRooms_shouldQueryAndMap() {
    testRoom.setArchivedAt(Instant.now());
    Page<Room> page = new PageImpl<>(List.of(testRoom), PageRequest.of(0, 10), 1);
    when(roomRepository.findArchivedRoomsPaged(eq(3), eq("MALE"), eq("302"), any()))
        .thenReturn(page);
    when(bedRepository.countByRoomIdAndStatus(roomId, BedStatus.AVAILABLE)).thenReturn(4L);
    when(bedRepository.countByRoomIdAndStatus(roomId, BedStatus.OCCUPIED)).thenReturn(0L);
    when(bedRepository.countByRoomIdAndStatus(roomId, BedStatus.MAINTENANCE)).thenReturn(0L);

    Page<ArchivedRoomSummaryResponse> result =
        roomService.getArchivedRooms(3, GenderPolicy.MALE, "302", PageRequest.of(0, 10));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent().get(0).archivedAt()).isNotNull();
    assertThat(result.getContent().get(0).availableBeds()).isEqualTo(4);
  }

  @Test
  @DisplayName(
      "getRoomDetails should include occupant CCCD when bed is occupied with active contract")
  void getRoomDetails_shouldIncludeOccupantCccd() {
    Tenant tenant =
        Tenant.builder()
            .id(UUID.randomUUID())
            .fullName("Nguyễn Văn A")
            .phone("0912345678")
            .idCardNumber("079203001187")
            .build();
    Bed bed =
        Bed.builder()
            .id(UUID.randomUUID())
            .room(testRoom)
            .bedCode("G01")
            .slotKey("P.302-G01")
            .status(BedStatus.OCCUPIED)
            .build();
    Contract contract =
        Contract.builder()
            .id(UUID.randomUUID())
            .tenant(tenant)
            .bed(bed)
            .status(ContractStatus.ACTIVE)
            .originalEndOn(LocalDate.of(2027, 9, 1))
            .build();

    when(roomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));
    when(bedRepository.findByRoomIdOrderByBedCodeAsc(roomId)).thenReturn(List.of(bed));
    when(contractRepository.findByBedIdInAndStatusIn(any(), any())).thenReturn(List.of(contract));
    when(roomAmenityRepository.findByRoomId(roomId)).thenReturn(List.of());

    RoomDetailResponse detail = roomService.getRoomDetails(roomId);

    assertThat(detail.beds()).hasSize(1);
    assertThat(detail.beds().get(0).occupant()).isNotNull();
    assertThat(detail.beds().get(0).occupant().idCardNumber()).isEqualTo("079203001187");
    assertThat(detail.beds().get(0).occupant().fullName()).isEqualTo("Nguyễn Văn A");
  }

  @Test
  @DisplayName(
      "createRoom maps protected registry code to ROOM_CODE_PROTECTED and unique races to ROOM_NUMBER_DUPLICATED")
  void createRoom_constraintClassification() {
    CreateRoomRequest request =
        new CreateRoomRequest(
            "P.302",
            3,
            RoomType.FOUR_BEDS,
            GenderPolicy.MALE,
            new BigDecimal("1500000.00"),
            List.of());

    when(roomRepository.existsByRoomNumber("P.302")).thenReturn(false);
    when(roomCodeRegistryRepository.findById("P.302"))
        .thenReturn(
            Optional.of(
                RoomCodeRegistry.builder()
                    .code("P.302")
                    .current(false)
                    .usedInBusiness(true)
                    .build()));

    assertThatThrownBy(() -> roomService.createRoom(request))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.ROOM_CODE_PROTECTED));

    when(roomCodeRegistryRepository.findById("P.302")).thenReturn(Optional.empty());
    when(roomRepository.saveAndFlush(any(Room.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "dup",
                new ConstraintViolationException(
                    "dup", new SQLException("dup", "23505"), "uq_rooms_number_canonical")));

    assertThatThrownBy(() -> roomService.createRoom(request))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.ROOM_NUMBER_DUPLICATED));

    // CHECK 23514 must propagate
    when(roomRepository.saveAndFlush(any(Room.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "check",
                new ConstraintViolationException(
                    "check", new SQLException("check", "23514"), "ck_rooms_base_price")));

    assertThatThrownBy(() -> roomService.createRoom(request))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("updateRoom rename maps slot/registry/room unique race to ROOM_NUMBER_DUPLICATED")
  void updateRoom_renameRace_shouldThrowRoomNumberDuplicated() {
    Bed bed =
        Bed.builder()
            .id(UUID.randomUUID())
            .room(testRoom)
            .bedCode("G01")
            .slotKey("P.302-G01")
            .status(BedStatus.AVAILABLE)
            .build();
    RoomCodeRegistry oldCode =
        RoomCodeRegistry.builder()
            .code("P.302")
            .room(testRoom)
            .current(true)
            .usedInBusiness(false)
            .build();

    when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(testRoom));
    when(bedRepository.findByRoomIdForUpdateOrderById(roomId)).thenReturn(List.of(bed));
    when(contractRepository.existsByBed_Room_IdAndStatusIn(eq(roomId), any())).thenReturn(false);
    when(roomCodeRegistryRepository.findById("P.303")).thenReturn(Optional.empty());
    when(roomCodeRegistryRepository.findByRoom_IdAndCurrentTrue(roomId))
        .thenReturn(Optional.of(oldCode));
    when(roomCodeRegistryRepository.saveAndFlush(any(RoomCodeRegistry.class)))
        .thenThrow(
            new DataIntegrityViolationException(
                "dup",
                new ConstraintViolationException(
                    "dup", new SQLException("dup", "23505"), "room_code_registry_pkey")));

    assertThatThrownBy(
            () ->
                roomService.updateRoom(
                    roomId,
                    new UpdateRoomRequest(
                        "P.303", GenderPolicy.MALE, new BigDecimal("1500000"), null)))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.ROOM_NUMBER_DUPLICATED));
  }

  @Test
  @DisplayName("deleteIncorrectRoom maps FK 23503 race to ROOM_HAS_HISTORY while rethrowing non-FK")
  void deleteIncorrectRoom_fkRace_shouldThrowRoomHasHistory() {
    Bed bed =
        Bed.builder().id(UUID.randomUUID()).room(testRoom).status(BedStatus.AVAILABLE).build();
    when(roomRepository.findByIdUnfilteredForUpdate(roomId)).thenReturn(Optional.of(testRoom));
    when(bedRepository.findByRoomIdForUpdateOrderById(roomId)).thenReturn(List.of(bed));
    when(roomHistoryGuard.hasProtectedHistory(eq(roomId), any())).thenReturn(false);

    org.mockito.Mockito.doThrow(
            new DataIntegrityViolationException(
                "fk",
                new ConstraintViolationException(
                    "fk", new SQLException("fk", "23503"), "fk_contracts_bed")))
        .when(roomRepository)
        .flush();

    assertThatThrownBy(() -> roomService.deleteIncorrectRoom(roomId))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.ROOM_HAS_HISTORY));

    org.mockito.Mockito.doThrow(
            new DataIntegrityViolationException(
                "check",
                new ConstraintViolationException(
                    "check", new SQLException("check", "23514"), "ck_something")))
        .when(roomRepository)
        .flush();

    assertThatThrownBy(() -> roomService.deleteIncorrectRoom(roomId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
