package com.uit.dormmanagement.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractDocument;
import com.uit.dormmanagement.entities.ContractRenewal;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.RoomCodeRegistry;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ContractVehicleRepositoryTest {

  @Autowired private EntityManager entityManager;
  @Autowired private ContractRepository contractRepository;
  @Autowired private ContractDocumentRepository contractDocumentRepository;
  @Autowired private ContractRenewalRepository contractRenewalRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private RoomCodeRegistryRepository roomCodeRegistryRepository;

  private Tenant testTenant;
  private Contract testContract;

  @BeforeEach
  void setUp() {
    Room room =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.401")
                .floor(4)
                .totalBeds((short) 4)
                .genderPolicy(GenderPolicy.MALE)
                .basePrice(new BigDecimal("1500000.00"))
                .build());

    roomCodeRegistryRepository.save(
        RoomCodeRegistry.builder()
            .code("P.401")
            .room(room)
            .current(true)
            .usedInBusiness(false)
            .build());

    Bed bed =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("G1")
                .slotKey("P.401-G1")
                .status(BedStatus.OCCUPIED)
                .build());

    User user =
        userRepository.save(
            User.builder()
                .email("nam.nguyen@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());

    testTenant =
        tenantRepository.save(
            Tenant.builder()
                .user(user)
                .tenantCode("SV-2026-0001")
                .idCardNumber("079204001234")
                .fullName("Nguyễn Hoàng Nam")
                .gender(GenderPolicy.MALE)
                .phone("0901234567")
                .permanentAddress("123 Lê Lợi, Q1, TP.HCM")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    testContract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-2026-0187")
                .tenant(testTenant)
                .bed(bed)
                .roomCodeSnapshot(room.getRoomNumber())
                .bedCodeSnapshot(bed.getBedCode())
                .startOn(LocalDate.of(2026, 9, 1))
                .originalEndOn(LocalDate.of(2027, 8, 31))
                .originalMonthlyRent(new BigDecimal("1500000.00"))
                .depositAmount(new BigDecimal("1500000.00"))
                .status(ContractStatus.ACTIVE)
                .build());
  }

  @Test
  @DisplayName("Should save multiple contract documents for single contract and query by category")
  void shouldSaveAndQueryMultipleContractDocuments() {
    ContractDocument doc1 =
        contractDocumentRepository.save(
            ContractDocument.builder()
                .contract(testContract)
                .originalFilename("hop-dong-goc-trang1.jpg")
                .storageFileKey("contracts/doc1.jpg")
                .mimeType("image/jpeg")
                .documentType(ContractDocumentCategory.ORIGINAL_CONTRACT)
                .build());

    ContractDocument doc2 =
        contractDocumentRepository.save(
            ContractDocument.builder()
                .contract(testContract)
                .originalFilename("hop-dong-goc-trang2.jpg")
                .storageFileKey("contracts/doc2.jpg")
                .mimeType("image/jpeg")
                .documentType(ContractDocumentCategory.ORIGINAL_CONTRACT)
                .build());

    ContractRenewal renewal =
        contractRenewalRepository.save(
            ContractRenewal.builder()
                .contract(testContract)
                .sequenceNo(1)
                .signedOn(LocalDate.of(2027, 8, 15))
                .previousEndOn(LocalDate.of(2027, 8, 31))
                .effectiveFrom(LocalDate.of(2027, 9, 1))
                .newEndOn(LocalDate.of(2027, 11, 30))
                .monthlyRent(new BigDecimal("1500000.00"))
                .build());

    ContractDocument annex =
        contractDocumentRepository.save(
            ContractDocument.builder()
                .contract(testContract)
                .renewal(renewal)
                .originalFilename("phu-luc-gia-han-3-thang.pdf")
                .storageFileKey("contracts/annex.pdf")
                .mimeType("application/pdf")
                .documentType(ContractDocumentCategory.RENEWAL_ANNEX)
                .build());

    List<ContractDocument> allDocs =
        contractDocumentRepository.findByContractId(testContract.getId());
    assertThat(allDocs).hasSize(3);

    List<ContractDocument> originalDocs =
        contractDocumentRepository.findByContractIdAndDocumentType(
            testContract.getId(), ContractDocumentCategory.ORIGINAL_CONTRACT);
    assertThat(originalDocs)
        .hasSize(2)
        .extracting(ContractDocument::getFileName)
        .containsExactlyInAnyOrder("hop-dong-goc-trang1.jpg", "hop-dong-goc-trang2.jpg");

    List<ContractDocument> annexDocs =
        contractDocumentRepository.findByContractIdAndDocumentType(
            testContract.getId(), ContractDocumentCategory.RENEWAL_ANNEX);
    assertThat(annexDocs).hasSize(1);
    assertThat(annexDocs.getFirst().getFileName()).isEqualTo("phu-luc-gia-han-3-thang.pdf");
  }

  @Test
  @DisplayName("Vehicle should store separate brand and color, and manage status transitions")
  void shouldStoreVehicleAndManageStatusTransitions() {
    Vehicle vehicle =
        vehicleRepository.save(
            Vehicle.builder()
                .tenant(testTenant)
                .licensePlate("59-X3 789.12")
                .brand("Honda Air Blade")
                .color("Đen nhám")
                .vehicleType(VehicleType.SCOOTER)
                .cardCode("THE-0187")
                .status(VehicleStatus.ISSUED)
                .registrationFrontFileKey("vehicles/front.jpg")
                .registrationBackFileKey("vehicles/back.jpg")
                .issuedAt(Instant.now())
                .build());

    entityManager.flush();
    entityManager.clear();

    Optional<Vehicle> found = vehicleRepository.findByLicensePlate("59-X3 789.12");
    assertThat(found).isPresent();
    assertThat(found.get().getBrand()).isEqualTo("Honda Air Blade");
    assertThat(found.get().getColor()).isEqualTo("Đen nhám");
    assertThat(found.get().getCardCode()).isEqualTo("THE-0187");
    assertThat(found.get().getStatus()).isEqualTo(VehicleStatus.ISSUED);

    // Test transition to REVOKED
    Vehicle vehicleToRevoke = found.get();
    vehicleToRevoke.setStatus(VehicleStatus.REVOKED);
    vehicleToRevoke.setRevokedAt(Instant.now());
    vehicleRepository.save(vehicleToRevoke);

    entityManager.flush();
    entityManager.clear();

    Optional<Vehicle> afterRevoke = vehicleRepository.findByLicensePlate("59-X3 789.12");
    assertThat(afterRevoke).isPresent();
    assertThat(afterRevoke.get().getStatus()).isEqualTo(VehicleStatus.REVOKED);
    assertThat(afterRevoke.get().getRevokedAt()).isNotNull();
  }
}
