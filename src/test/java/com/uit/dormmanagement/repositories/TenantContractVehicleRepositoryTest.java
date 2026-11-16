package com.uit.dormmanagement.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractDocument;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TenantContractVehicleRepositoryTest {

  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private ContractDocumentRepository contractDocumentRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private EntityManager entityManager;

  private Room testRoom;
  private Bed testBed;
  private Tenant testTenant;
  private Contract testContract;

  @BeforeEach
  void setUp() {
    testRoom =
        roomRepository.save(
            Room.builder()
                .roomNumber("P.999")
                .floor(9)
                .genderPolicy(GenderPolicy.MALE)
                .totalBeds((short) 4)
                .basePrice(new BigDecimal("1500000.00"))
                .build());

    testBed =
        bedRepository.save(
            Bed.builder()
                .room(testRoom)
                .bedCode("B01")
                .slotKey("P.999-B01")
                .status(BedStatus.OCCUPIED)
                .build());

    User tenantUser =
        userRepository.save(
            User.builder()
                .email("tenant-repo@example.test")
                .passwordHash("hash")
                .role(UserRole.TENANT)
                .status(UserStatus.ACTIVE)
                .build());
    testTenant =
        tenantRepository.save(
            Tenant.builder()
                .user(tenantUser)
                .tenantCode("NT-9001")
                .fullName("Nguyễn Văn Repo")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203999001")
                .phone("0987654321")
                .permanentAddress("123 Test Street")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    testContract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-2026-9001")
                .tenant(testTenant)
                .bed(testBed)
                .roomCodeSnapshot(testRoom.getRoomNumber())
                .bedCodeSnapshot(testBed.getBedCode())
                .startOn(LocalDate.of(2026, 1, 1))
                .originalEndOn(LocalDate.of(2026, 6, 30))
                .originalMonthlyRent(new BigDecimal("1500000.00"))
                .depositAmount(new BigDecimal("1500000.00"))
                .status(ContractStatus.ACTIVE)
                .build());

    entityManager.flush();
    entityManager.clear();
  }

  // ===================== TENANT REPOSITORY TESTS =====================

  @Test
  @DisplayName(
      "TenantRepository: findActiveTenantsWithFilters should filter by search, roomId, and status")
  void tenantRepository_findActiveTenantsWithFilters() {
    Page<Tenant> pageAll =
        tenantRepository.findActiveTenantsWithFilters(null, null, null, PageRequest.of(0, 10));
    assertThat(pageAll.getContent()).isNotEmpty();

    Page<Tenant> pageSearch =
        tenantRepository.findActiveTenantsWithFilters(
            "Văn Repo", null, null, PageRequest.of(0, 10));
    assertThat(pageSearch.getContent()).hasSize(1);
    assertThat(pageSearch.getContent().get(0).getTenantCode()).isEqualTo("NT-9001");

    Page<Tenant> pageRoom =
        tenantRepository.findActiveTenantsWithFilters(
            null, testRoom.getId(), null, PageRequest.of(0, 10));
    assertThat(pageRoom.getContent()).hasSize(1);
    assertThat(pageRoom.getContent().get(0).getIdCardNumber()).isEqualTo("079203999001");

    Page<Tenant> pageStatus =
        tenantRepository.findActiveTenantsWithFilters(
            null, null, ResidentStatus.CHECKED_OUT, PageRequest.of(0, 10));
    assertThat(pageStatus.getContent()).noneMatch(t -> t.getTenantCode().equals("NT-9001"));
  }

  @Test
  @DisplayName("TenantRepository: existsByIdCardNumberAndResidentStatus and sequence count queries")
  void tenantRepository_extraQueries() {
    boolean existsActive =
        tenantRepository.existsByIdCardNumberAndResidentStatus(
            "079203999001", ResidentStatus.ACTIVE);
    assertThat(existsActive).isTrue();

    boolean existsCheckedOut =
        tenantRepository.existsByIdCardNumberAndResidentStatus(
            "079203999001", ResidentStatus.CHECKED_OUT);
    assertThat(existsCheckedOut).isFalse();

    Optional<Tenant> byEmail = tenantRepository.findByUser_Email("tenant-repo@example.test");
    assertThat(byEmail).isPresent();
    assertThat(byEmail.get().getTenantCode()).isEqualTo("NT-9001");

    long countWithPrefix = tenantRepository.countByTenantCodeStartingWith("NT-");
    assertThat(countWithPrefix).isGreaterThanOrEqualTo(1);

    long nextSeq = tenantRepository.findNextTenantCodeSequence();
    assertThat(nextSeq).isGreaterThanOrEqualTo(9002);
  }

  // ===================== VEHICLE REPOSITORY TESTS =====================

  @Test
  @DisplayName("VehicleRepository: count, findFirst, and exists checks")
  void vehicleRepository_queries() {
    Vehicle vehicle =
        vehicleRepository.save(
            Vehicle.builder()
                .tenant(testTenant)
                .vehicleType(VehicleType.SCOOTER)
                .licensePlate("59-X1 999.99")
                .brand("Honda")
                .color("Red")
                .cardCode("CARD-999")
                .status(VehicleStatus.ISSUED)
                .build());
    entityManager.flush();
    entityManager.clear();

    long count =
        vehicleRepository.countByTenantIdAndStatusIn(
            testTenant.getId(), List.of(VehicleStatus.PENDING_CARD, VehicleStatus.ISSUED));
    assertThat(count).isEqualTo(1);

    Optional<Vehicle> first =
        vehicleRepository.findFirstByTenantIdAndStatusIn(
            testTenant.getId(), List.of(VehicleStatus.ISSUED));
    assertThat(first).isPresent();
    assertThat(first.get().getLicensePlate()).isEqualTo("59-X1 999.99");

    boolean existsPlate =
        vehicleRepository.existsByLicensePlateAndStatusIn(
            "59-X1 999.99", List.of(VehicleStatus.ISSUED));
    assertThat(existsPlate).isTrue();

    boolean existsCard =
        vehicleRepository.existsByCardCodeAndStatus(
            "CARD-999", VehicleStatus.ISSUED);
    assertThat(existsCard).isTrue();

    List<Vehicle> tenantVehicles =
        vehicleRepository.findByTenantId(testTenant.getId());
    assertThat(tenantVehicles).hasSize(1);

    Page<Vehicle> filtered =
        vehicleRepository.findVehiclesWithFilters(
            "59-X1", VehicleStatus.ISSUED, PageRequest.of(0, 10));
    assertThat(filtered.getContent()).hasSize(1);
    assertThat(filtered.getContent().get(0).getBrand()).isEqualTo("Honda");
  }

  // ===================== CONTRACT REPOSITORY TESTS =====================

  @Test
  @DisplayName("ContractRepository: filter, expiring check, sequence, and tenant lookup")
  void contractRepository_queries() {
    Page<Contract> page =
        contractRepository.findContractsWithFilters(
            "9001", ContractStatus.ACTIVE, PageRequest.of(0, 10));
    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).getContractCode()).isEqualTo("HD-2026-9001");

    List<Contract> expiring =
        contractRepository.findAllByStatusAndOriginalEndOnLessThanEqual(
            ContractStatus.ACTIVE, LocalDate.of(2026, 7, 1));
    assertThat(expiring).isNotEmpty();
    assertThat(expiring).anyMatch(c -> c.getContractCode().equals("HD-2026-9001"));

    Optional<Contract> activeContract =
        contractRepository.findFirstByTenantIdAndStatusIn(
            testTenant.getId(), List.of(ContractStatus.ACTIVE));
    assertThat(activeContract).isPresent();
    assertThat(activeContract.get().getContractCode()).isEqualTo("HD-2026-9001");

    long countStarting = contractRepository.countByContractCodeStartingWith("HD-2026-");
    assertThat(countStarting).isGreaterThanOrEqualTo(1);

    long nextSeq = contractRepository.findNextContractCodeSequence(2026);
    assertThat(nextSeq).isGreaterThanOrEqualTo(9002);
  }

  // ===================== CONTRACT DOCUMENT REPOSITORY TESTS =====================

  @Test
  @DisplayName("ContractDocumentRepository: findByContractIdOrderByUploadedAtDesc")
  void contractDocumentRepository_queries() {
    ContractDocument doc1 =
        contractDocumentRepository.save(
            ContractDocument.builder()
                .contract(testContract)
                .originalFilename("doc1.pdf")
                .storageFileKey("contracts/doc1.pdf")
                .mimeType("application/pdf")
                .documentType(ContractDocumentCategory.ORIGINAL_CONTRACT)
                .uploadedAt(Instant.now().minusSeconds(100))
                .build());

    ContractDocument doc2 =
        contractDocumentRepository.save(
            ContractDocument.builder()
                .contract(testContract)
                .originalFilename("doc2.pdf")
                .storageFileKey("contracts/doc2.pdf")
                .mimeType("application/pdf")
                .documentType(ContractDocumentCategory.CHECKOUT_HANDOVER)
                .uploadedAt(Instant.now())
                .build());

    entityManager.flush();
    entityManager.clear();

    List<ContractDocument> docs =
        contractDocumentRepository.findByContractIdOrderByUploadedAtDesc(testContract.getId());
    assertThat(docs).hasSize(2);
    assertThat(docs.get(0).getFileName()).isEqualTo("doc2.pdf");
    assertThat(docs.get(1).getFileName()).isEqualTo("doc1.pdf");
  }
}
