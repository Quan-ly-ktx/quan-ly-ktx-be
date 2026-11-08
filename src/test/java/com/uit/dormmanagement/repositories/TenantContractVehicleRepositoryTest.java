package com.uit.dormmanagement.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractDocument;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.RoomType;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TenantContractVehicleRepositoryTest {

  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private TenantRepository tenantRepository;
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
                .roomType(RoomType.FOUR_BEDS)
                .genderPolicy(GenderPolicy.MALE)
                .totalBeds(4)
                .occupiedBeds(1)
                .basePrice(new BigDecimal("1500000.00"))
                .build());

    testBed =
        bedRepository.save(
            Bed.builder()
                .room(testRoom)
                .bedCode("B01")
                .slotKey("P.999-B01")
                .price(new BigDecimal("1500000.00"))
                .status(BedStatus.OCCUPIED)
                .build());

    testTenant =
        tenantRepository.save(
            Tenant.builder()
                .tenantCode("NT-9001")
                .fullName("Nguyễn Văn Repo")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203999001")
                .phone("0987654321")
                .email("repo.test@dorm.vn")
                .permanentAddress("123 Test Street")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    testContract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-2026-9001")
                .tenant(testTenant)
                .bed(testBed)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .monthlyRent(new BigDecimal("1500000.00"))
                .depositAmount(new BigDecimal("1500000.00"))
                .status(ContractStatus.ACTIVE)
                .signedAt(Instant.now())
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

    Optional<Tenant> byEmail = tenantRepository.findByEmail("repo.test@dorm.vn");
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
                .status(VehicleStatus.ACTIVE)
                .build());
    entityManager.flush();
    entityManager.clear();

    long count =
        vehicleRepository.countByTenantIdAndStatusInAndDeletedAtIsNull(
            testTenant.getId(), List.of(VehicleStatus.PENDING, VehicleStatus.ACTIVE));
    assertThat(count).isEqualTo(1);

    Optional<Vehicle> first =
        vehicleRepository.findFirstByTenantIdAndStatusInAndDeletedAtIsNull(
            testTenant.getId(), List.of(VehicleStatus.ACTIVE));
    assertThat(first).isPresent();
    assertThat(first.get().getLicensePlate()).isEqualTo("59-X1 999.99");

    boolean existsPlate =
        vehicleRepository.existsByLicensePlateAndStatusInAndDeletedAtIsNull(
            "59-X1 999.99", List.of(VehicleStatus.ACTIVE));
    assertThat(existsPlate).isTrue();

    boolean existsCard =
        vehicleRepository.existsByCardCodeAndStatusAndDeletedAtIsNull(
            "CARD-999", VehicleStatus.ACTIVE);
    assertThat(existsCard).isTrue();

    List<Vehicle> tenantVehicles =
        vehicleRepository.findByTenantIdAndDeletedAtIsNull(testTenant.getId());
    assertThat(tenantVehicles).hasSize(1);

    Page<Vehicle> filtered =
        vehicleRepository.findVehiclesWithFilters(
            "59-X1", VehicleStatus.ACTIVE, PageRequest.of(0, 10));
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
        contractRepository.findAllByStatusAndEndDateLessThanEqual(
            ContractStatus.ACTIVE, LocalDate.of(2026, 7, 1));
    assertThat(expiring).isNotEmpty();
    assertThat(expiring).anyMatch(c -> c.getContractCode().equals("HD-2026-9001"));

    Optional<Contract> activeContract =
        contractRepository.findFirstByTenantIdAndStatusInAndDeletedAtIsNull(
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
                .fileName("doc1.pdf")
                .fileUrl("http://example.com/doc1.pdf")
                .fileType("application/pdf")
                .fileSize(1024L)
                .documentCategory(ContractDocumentCategory.ORIGINAL_CONTRACT)
                .uploadedAt(Instant.now().minusSeconds(100))
                .build());

    ContractDocument doc2 =
        contractDocumentRepository.save(
            ContractDocument.builder()
                .contract(testContract)
                .fileName("doc2.pdf")
                .fileUrl("http://example.com/doc2.pdf")
                .fileType("application/pdf")
                .fileSize(2048L)
                .documentCategory(ContractDocumentCategory.RENEWAL_ANNEX)
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
