package com.uit.dormmanagement.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class V7SchemaMigrationTest {

  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private EntityManager entityManager;

  private Tenant tenant1;
  private Tenant tenant2;

  @BeforeEach
  void setUp() {
    Room room =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.301")
                .floor(3)
                .roomType(RoomType.FOUR_BEDS)
                .genderPolicy(GenderPolicy.MALE)
                .totalBeds(4)
                .basePrice(new BigDecimal("1500000.00"))
                .build());

    Bed bed1 =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("G01")
                .slotKey("P.301-G01")
                .price(new BigDecimal("1500000.00"))
                .status(BedStatus.OCCUPIED)
                .build());

    Bed bed2 =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("G02")
                .slotKey("P.301-G02")
                .price(new BigDecimal("1500000.00"))
                .status(BedStatus.OCCUPIED)
                .build());

    tenant1 =
        tenantRepository.save(
            Tenant.builder()
                .tenantCode("NT-0001")
                .fullName("Nguyen Van A")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203000001")
                .phone("0987000001")
                .email("nva@gmail.com")
                .permanentAddress("TP.HCM")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    tenant2 =
        tenantRepository.save(
            Tenant.builder()
                .tenantCode("NT-0002")
                .fullName("Tran Van B")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203000002")
                .phone("0987000002")
                .email("tvb@gmail.com")
                .permanentAddress("TP.HCM")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    contractRepository.save(
        Contract.builder()
            .contractCode("HD-2026-0001")
            .tenant(tenant1)
            .bed(bed1)
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 9, 1))
            .monthlyRent(new BigDecimal("1500000.00"))
            .depositAmount(new BigDecimal("1500000.00"))
            .status(ContractStatus.ACTIVE)
            .build());

    contractRepository.save(
        Contract.builder()
            .contractCode("HD-2026-0002")
            .tenant(tenant2)
            .bed(bed2)
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 9, 1))
            .monthlyRent(new BigDecimal("1500000.00"))
            .depositAmount(new BigDecimal("1500000.00"))
            .status(ContractStatus.ACTIVE)
            .build());
  }

  @Test
  @DisplayName("cardCode: when revoked, allows reissuing the same cardCode to another vehicle")
  void cardCode_whenRevoked_allowsReissuingToAnotherVehicle() {
    // 1. Insert revoked vehicle with card_code 'THE-001'
    Vehicle v1 =
        vehicleRepository.save(
            Vehicle.builder()
                .tenant(tenant1)
                .licensePlate("59-A1 111.11")
                .brand("Honda")
                .color("Black")
                .vehicleType(VehicleType.SCOOTER)
                .cardCode("THE-001")
                .status(VehicleStatus.REVOKED)
                .build());
    entityManager.flush();

    // 2. Insert active vehicle with same card_code 'THE-001' for another tenant
    // -> should succeed because V7 dropped the rigid full-table UNIQUE constraint
    // and replaced it with a partial index only on ACTIVE vehicles
    Vehicle v2 =
        vehicleRepository.save(
            Vehicle.builder()
                .tenant(tenant2)
                .licensePlate("59-A2 222.22")
                .brand("Yamaha")
                .color("Red")
                .vehicleType(VehicleType.SCOOTER)
                .cardCode("THE-001")
                .status(VehicleStatus.ACTIVE)
                .build());
    entityManager.flush();
    entityManager.clear();

    Optional<Vehicle> foundRevoked = vehicleRepository.findById(v1.getId());
    Optional<Vehicle> foundActive = vehicleRepository.findById(v2.getId());

    assertThat(foundRevoked).isPresent();
    assertThat(foundActive).isPresent();
    assertThat(foundRevoked.get().getStatus()).isEqualTo(VehicleStatus.REVOKED);
    assertThat(foundActive.get().getStatus()).isEqualTo(VehicleStatus.ACTIVE);
    assertThat(foundRevoked.get().getCardCode()).isEqualTo(foundActive.get().getCardCode());
  }
}
