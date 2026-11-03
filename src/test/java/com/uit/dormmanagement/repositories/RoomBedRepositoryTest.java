package com.uit.dormmanagement.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.RoomType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RoomBedRepositoryTest {

  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private TenantRepository tenantRepository;

  @Test
  @DisplayName(
      "findRoomsWithFilters should filter rooms by floor, genderPolicy, and search keyword")
  void findRoomsWithFilters_shouldFilterAccurately() {
    Room room1 =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.301")
                .floor(3)
                .roomType(RoomType.FOUR_BEDS)
                .genderPolicy(GenderPolicy.MALE)
                .totalBeds(4)
                .basePrice(new BigDecimal("1500000.00"))
                .build());

    Room room2 =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.302")
                .floor(3)
                .roomType(RoomType.SIX_BEDS)
                .genderPolicy(GenderPolicy.FEMALE)
                .totalBeds(6)
                .basePrice(new BigDecimal("1400000.00"))
                .build());

    Room room3 =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.401")
                .floor(4)
                .roomType(RoomType.FOUR_BEDS)
                .genderPolicy(GenderPolicy.MALE)
                .totalBeds(4)
                .basePrice(new BigDecimal("1600000.00"))
                .build());

    List<Room> floor3MaleRooms = roomRepository.findRoomsWithFilters(3, GenderPolicy.MALE, null);
    assertThat(floor3MaleRooms).hasSize(1);
    assertThat(floor3MaleRooms.get(0).getRoomNumber()).isEqualTo("P.301");

    List<Room> searchRooms = roomRepository.findRoomsWithFilters(null, null, "302");
    assertThat(searchRooms).hasSize(1);
    assertThat(searchRooms.get(0).getRoomNumber()).isEqualTo("P.302");

    Page<Room> paged = roomRepository.findRoomsPaged(null, null, null, PageRequest.of(0, 10));
    assertThat(paged.getTotalElements()).isGreaterThanOrEqualTo(3);
  }

  @Test
  @DisplayName("findByRoomIdOrderByBedCodeAsc should return sorted beds for a room")
  void findByRoomIdOrderByBedCodeAsc_shouldReturnSortedBeds() {
    Room room =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.201")
                .floor(2)
                .roomType(RoomType.FOUR_BEDS)
                .genderPolicy(GenderPolicy.MALE)
                .totalBeds(4)
                .basePrice(new BigDecimal("1500000.00"))
                .build());

    Bed bed2 =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("G02")
                .slotKey("P.201-G02")
                .price(new BigDecimal("1500000.00"))
                .status(BedStatus.AVAILABLE)
                .build());

    Bed bed1 =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("G01")
                .slotKey("P.201-G01")
                .price(new BigDecimal("1500000.00"))
                .status(BedStatus.AVAILABLE)
                .build());

    List<Bed> beds = bedRepository.findByRoomIdOrderByBedCodeAsc(room.getId());
    assertThat(beds).hasSize(2);
    assertThat(beds.get(0).getBedCode()).isEqualTo("G01");
    assertThat(beds.get(1).getBedCode()).isEqualTo("G02");
  }

  @Test
  @DisplayName(
      "existsByBed_Room_IdAndStatusInAndDeletedAtIsNull should detect active contracts in room")
  void existsByBedRoomId_shouldDetectActiveContracts() {
    Room room =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.102")
                .floor(1)
                .roomType(RoomType.FOUR_BEDS)
                .genderPolicy(GenderPolicy.MALE)
                .totalBeds(4)
                .basePrice(new BigDecimal("1500000.00"))
                .build());

    Bed bed =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("G01")
                .slotKey("P.102-G01")
                .price(new BigDecimal("1500000.00"))
                .status(BedStatus.OCCUPIED)
                .build());

    Tenant tenant =
        tenantRepository.save(
            Tenant.builder()
                .tenantCode("TN-001")
                .fullName("Nguyễn Văn An")
                .gender(GenderPolicy.MALE)
                .idCardNumber("123456789012")
                .phone("0901234567")
                .email("an.nguyen@example.com")
                .permanentAddress("Hà Nội")
                .build());

    contractRepository.save(
        Contract.builder()
            .contractCode("HD-001")
            .tenant(tenant)
            .bed(bed)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusMonths(6))
            .monthlyRent(new BigDecimal("1500000.00"))
            .depositAmount(new BigDecimal("1500000.00"))
            .status(ContractStatus.ACTIVE)
            .build());

    boolean hasActiveContract =
        contractRepository.existsByBed_Room_IdAndStatusInAndDeletedAtIsNull(
            room.getId(), Set.of(ContractStatus.ACTIVE, ContractStatus.EXPIRING_SOON));
    assertThat(hasActiveContract).isTrue();

    boolean bedHasActiveContract =
        contractRepository.existsByBedIdAndStatusInAndDeletedAtIsNull(
            bed.getId(), Set.of(ContractStatus.ACTIVE, ContractStatus.EXPIRING_SOON));
    assertThat(bedHasActiveContract).isTrue();
  }
}
