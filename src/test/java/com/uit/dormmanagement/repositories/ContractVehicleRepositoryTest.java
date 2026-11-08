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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ContractVehicleRepositoryTest {

  @Autowired private RoomRepository roomRepository;
  @Autowired private BedRepository bedRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private ContractDocumentRepository contractDocumentRepository;
  @Autowired private VehicleRepository vehicleRepository;
  @Autowired private EntityManager entityManager;

  private Tenant testTenant;
  private Contract testContract;

  @BeforeEach
  void setUp() {
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

    Bed bed =
        bedRepository.save(
            Bed.builder()
                .room(room)
                .bedCode("G01")
                .slotKey("P.201-G01")
                .price(new BigDecimal("1500000.00"))
                .status(BedStatus.OCCUPIED)
                .build());

    testTenant =
        tenantRepository.save(
            Tenant.builder()
                .tenantCode("NT-0187")
                .fullName("Nguyễn Hoàng Nam")
                .gender(GenderPolicy.MALE)
                .idCardNumber("079203001187")
                .phone("0987214506")
                .email("nam@gmail.com")
                .permanentAddress("TP.HCM")
                .residentStatus(ResidentStatus.ACTIVE)
                .build());

    testContract =
        contractRepository.save(
            Contract.builder()
                .contractCode("HD-2026-0187")
                .tenant(testTenant)
                .bed(bed)
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2027, 9, 1))
                .monthlyRent(new BigDecimal("1500000.00"))
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
                .fileName("hop-dong-goc-trang1.jpg")
                .fileUrl("https://storage.example.com/contracts/doc1.jpg")
                .fileType("image/jpeg")
                .fileSize(1024000L)
                .documentCategory(ContractDocumentCategory.ORIGINAL_CONTRACT)
                .build());

    ContractDocument doc2 =
        contractDocumentRepository.save(
            ContractDocument.builder()
                .contract(testContract)
                .fileName("hop-dong-goc-trang2.jpg")
                .fileUrl("https://storage.example.com/contracts/doc2.jpg")
                .fileType("image/jpeg")
                .fileSize(1048576L)
                .documentCategory(ContractDocumentCategory.ORIGINAL_CONTRACT)
                .build());

    ContractDocument annex =
        contractDocumentRepository.save(
            ContractDocument.builder()
                .contract(testContract)
                .fileName("phu-luc-gia-han-3-thang.pdf")
                .fileUrl("https://storage.example.com/contracts/annex.pdf")
                .fileType("application/pdf")
                .fileSize(2048000L)
                .documentCategory(ContractDocumentCategory.RENEWAL_ANNEX)
                .build());

    List<ContractDocument> allDocs =
        contractDocumentRepository.findByContractId(testContract.getId());
    assertThat(allDocs).hasSize(3);

    List<ContractDocument> originalDocs =
        contractDocumentRepository.findByContractIdAndDocumentCategory(
            testContract.getId(), ContractDocumentCategory.ORIGINAL_CONTRACT);
    assertThat(originalDocs)
        .hasSize(2)
        .extracting(ContractDocument::getFileName)
        .containsExactlyInAnyOrder("hop-dong-goc-trang1.jpg", "hop-dong-goc-trang2.jpg");

    List<ContractDocument> annexDocs =
        contractDocumentRepository.findByContractIdAndDocumentCategory(
            testContract.getId(), ContractDocumentCategory.RENEWAL_ANNEX);
    assertThat(annexDocs).hasSize(1);
    assertThat(annexDocs.get(0).getFileName()).isEqualTo("phu-luc-gia-han-3-thang.pdf");
  }

  @Test
  @DisplayName("Vehicle should store separate brand and color, and respect soft delete")
  void shouldStoreVehicleAndRespectSoftDelete() {
    Vehicle vehicle =
        vehicleRepository.save(
            Vehicle.builder()
                .tenant(testTenant)
                .licensePlate("59-X3 789.12")
                .brand("Honda Air Blade")
                .color("Đen nhám")
                .vehicleType(VehicleType.SCOOTER)
                .cardCode("THE-0187")
                .status(VehicleStatus.ACTIVE)
                .registrationFrontUrl("https://storage.example.com/front.jpg")
                .registrationBackUrl("https://storage.example.com/back.jpg")
                .build());

    entityManager.flush();
    entityManager.clear();

    Optional<Vehicle> found = vehicleRepository.findByLicensePlate("59-X3 789.12");
    assertThat(found).isPresent();
    assertThat(found.get().getBrand()).isEqualTo("Honda Air Blade");
    assertThat(found.get().getColor()).isEqualTo("Đen nhám");
    assertThat(found.get().getCardCode()).isEqualTo("THE-0187");

    // Test soft delete
    Vehicle vehicleToSoftDelete = found.get();
    vehicleToSoftDelete.setDeletedAt(Instant.now());
    vehicleRepository.save(vehicleToSoftDelete);

    entityManager.flush();
    entityManager.clear();

    // With @SQLRestriction("deleted_at IS NULL"), findByLicensePlate should not find the vehicle
    Optional<Vehicle> afterDelete = vehicleRepository.findByLicensePlate("59-X3 789.12");
    assertThat(afterDelete).isEmpty();
  }
}
