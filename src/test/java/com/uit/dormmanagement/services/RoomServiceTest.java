package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.request.room.CreateRoomRequest;
import com.uit.dormmanagement.dtos.request.room.UpdateRoomRequest;
import com.uit.dormmanagement.dtos.response.room.RoomDetailResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Room;
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
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.services.impl.RoomServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

  @Mock private RoomRepository roomRepository;
  @Mock private BedRepository bedRepository;
  @Mock private RoomAmenityRepository roomAmenityRepository;
  @Mock private AmenityRepository amenityRepository;
  @Mock private ContractRepository contractRepository;

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
            .roomType(RoomType.FOUR_BEDS)
            .genderPolicy(GenderPolicy.MALE)
            .totalBeds(4)
            .occupiedBeds(0)
            .basePrice(new BigDecimal("1500000.00"))
            .build();
  }

  @Test
  @DisplayName("createRoom should create room and generate N beds inheriting basePrice")
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
    when(roomRepository.save(any(Room.class)))
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

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Bed>> bedsCaptor = ArgumentCaptor.forClass(List.class);
    verify(bedRepository).saveAll(bedsCaptor.capture());

    List<Bed> generatedBeds = bedsCaptor.getValue();
    assertThat(generatedBeds).hasSize(4);
    assertThat(generatedBeds.get(0).getBedCode()).isEqualTo("G01");
    assertThat(generatedBeds.get(0).getSlotKey()).isEqualTo("P.302-G01");
    assertThat(generatedBeds.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("1500000.00"));
    assertThat(generatedBeds.get(0).getStatus()).isEqualTo(BedStatus.AVAILABLE);
    assertThat(generatedBeds.get(3).getBedCode()).isEqualTo("G04");
  }

  @Test
  @DisplayName("createRoom should throw ROOM_NUMBER_DUPLICATED when room number already exists")
  void createRoom_whenRoomNumberExists_shouldThrow() {
    CreateRoomRequest request =
        new CreateRoomRequest(
            "P.302",
            3,
            RoomType.FOUR_BEDS,
            GenderPolicy.MALE,
            new BigDecimal("1500000.00"),
            List.of());

    when(roomRepository.existsByRoomNumber("P.302")).thenReturn(true);

    assertThatThrownBy(() -> roomService.createRoom(request))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.ROOM_NUMBER_DUPLICATED));
  }

  @Test
  @DisplayName("updateRoom should allow genderPolicy change when occupiedBeds is 0")
  void updateRoom_whenNoOccupants_shouldAllowGenderPolicyChange() {
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));
    when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

    UpdateRoomRequest request =
        new UpdateRoomRequest(GenderPolicy.FEMALE, new BigDecimal("1700000.00"), List.of());

    RoomDetailResponse response = roomService.updateRoom(roomId, request);

    assertThat(response.genderPolicy()).isEqualTo(GenderPolicy.FEMALE);
    assertThat(response.basePrice()).isEqualByComparingTo(new BigDecimal("1700000.00"));
  }

  @Test
  @DisplayName("updateRoom should reject genderPolicy change when occupiedBeds > 0")
  void updateRoom_whenOccupied_shouldRejectGenderPolicyChange() {
    testRoom.setOccupiedBeds(2);
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));

    UpdateRoomRequest request =
        new UpdateRoomRequest(GenderPolicy.FEMALE, new BigDecimal("1500000.00"), List.of());

    assertThatThrownBy(() -> roomService.updateRoom(roomId, request))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.CANNOT_CHANGE_GENDER_POLICY));
  }

  @Test
  @DisplayName("updateRoom should synchronize price of all child beds when basePrice changes")
  void updateRoom_shouldSyncBedPrices() {
    Bed bed1 =
        Bed.builder()
            .id(UUID.randomUUID())
            .room(testRoom)
            .price(new BigDecimal("1500000.00"))
            .status(BedStatus.AVAILABLE)
            .build();
    Bed bed2 =
        Bed.builder()
            .id(UUID.randomUUID())
            .room(testRoom)
            .price(new BigDecimal("1500000.00"))
            .status(BedStatus.AVAILABLE)
            .build();

    when(roomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));
    when(bedRepository.findByRoomIdOrderByBedCodeAsc(roomId)).thenReturn(List.of(bed1, bed2));
    when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

    UpdateRoomRequest request =
        new UpdateRoomRequest(GenderPolicy.MALE, new BigDecimal("1800000.00"), List.of());

    roomService.updateRoom(roomId, request);

    assertThat(bed1.getPrice()).isEqualByComparingTo(new BigDecimal("1800000.00"));
    assertThat(bed2.getPrice()).isEqualByComparingTo(new BigDecimal("1800000.00"));
    verify(bedRepository).saveAll(any());
  }

  @Test
  @DisplayName("deleteRoom should throw CANNOT_DELETE_OCCUPIED_ROOM when occupiedBeds > 0")
  void deleteRoom_whenOccupied_shouldThrow() {
    testRoom.setOccupiedBeds(1);
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));

    assertThatThrownBy(() -> roomService.deleteRoom(roomId))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.CANNOT_DELETE_OCCUPIED_ROOM));
  }

  @Test
  @DisplayName("deleteRoom should throw CANNOT_DELETE_OCCUPIED_ROOM when active contracts exist")
  void deleteRoom_whenActiveContractsExist_shouldThrow() {
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));
    when(contractRepository.existsByBed_Room_IdAndStatusInAndDeletedAtIsNull(
            eq(roomId), eq(Set.of(ContractStatus.ACTIVE, ContractStatus.EXPIRING_SOON))))
        .thenReturn(true);

    assertThatThrownBy(() -> roomService.deleteRoom(roomId))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex ->
                assertThat(((BaseAppException) ex).getErrorCode())
                    .isEqualTo(RoomErrorCode.CANNOT_DELETE_OCCUPIED_ROOM));
  }

  @Test
  @DisplayName("deleteRoom should soft delete room when vacant and only terminated contracts exist")
  void deleteRoom_whenVacantAndNoActiveContracts_shouldSoftDelete() {
    when(roomRepository.findById(roomId)).thenReturn(Optional.of(testRoom));
    when(contractRepository.existsByBed_Room_IdAndStatusInAndDeletedAtIsNull(
            eq(roomId), eq(Set.of(ContractStatus.ACTIVE, ContractStatus.EXPIRING_SOON))))
        .thenReturn(false);

    roomService.deleteRoom(roomId);

    assertThat(testRoom.getDeletedAt()).isNotNull();
    verify(roomRepository).save(testRoom);
  }

  @Test
  @DisplayName("getRoomMatrix should return rooms with beds and occupant details")
  void getRoomMatrix_shouldIncludeOccupantDetails() {
    Bed bed1 =
        Bed.builder()
            .id(UUID.randomUUID())
            .room(testRoom)
            .bedCode("G01")
            .slotKey("P.302-G01")
            .price(new BigDecimal("1500000.00"))
            .status(BedStatus.OCCUPIED)
            .build();

    Tenant tenant =
        Tenant.builder()
            .id(UUID.randomUUID())
            .fullName("Nguyễn Văn An")
            .phone("0901234567")
            .build();

    Contract contract =
        Contract.builder()
            .id(UUID.randomUUID())
            .bed(bed1)
            .tenant(tenant)
            .status(ContractStatus.ACTIVE)
            .endDate(LocalDate.now().plusMonths(3))
            .build();

    when(roomRepository.findRoomsWithFilters(3, GenderPolicy.MALE, null))
        .thenReturn(List.of(testRoom));
    when(bedRepository.findByRoomIdOrderByBedCodeAsc(roomId)).thenReturn(List.of(bed1));
    when(contractRepository.findByBedIdInAndStatusInAndDeletedAtIsNull(any(), any()))
        .thenReturn(List.of(contract));

    List<RoomDetailResponse> matrix = roomService.getRoomMatrix(3, GenderPolicy.MALE, null);

    assertThat(matrix).hasSize(1);
    assertThat(matrix.get(0).beds()).hasSize(1);
    assertThat(matrix.get(0).beds().get(0).occupant()).isNotNull();
    assertThat(matrix.get(0).beds().get(0).occupant().fullName()).isEqualTo("Nguyễn Văn An");
  }
}
