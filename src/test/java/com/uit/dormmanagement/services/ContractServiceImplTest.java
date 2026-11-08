package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.request.contract.RenewContractRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractSummaryResponse;
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
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractDocumentRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.RoomRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.impl.ContractServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ContractServiceImplTest {

  @Mock private ContractRepository contractRepository;
  @Mock private ContractDocumentRepository contractDocumentRepository;
  @Mock private BedRepository bedRepository;
  @Mock private RoomRepository roomRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private VehicleRepository vehicleRepository;
  @Mock private StorageService storageService;

  @InjectMocks private ContractServiceImpl contractService;

  private UUID contractId;
  private Contract contract;
  private Room room;
  private Bed bed;
  private Tenant tenant;
  private Vehicle vehicle;

  @BeforeEach
  void setUp() {
    contractId = UUID.randomUUID();

    room = Room.builder().id(UUID.randomUUID()).roomNumber("P.302").occupiedBeds(1).build();
    bed =
        Bed.builder()
            .id(UUID.randomUUID())
            .bedCode("G02")
            .status(BedStatus.OCCUPIED)
            .room(room)
            .build();

    tenant =
        Tenant.builder()
            .id(UUID.randomUUID())
            .tenantCode("NT-0187")
            .fullName("Nguyễn Hoàng Nam")
            .gender(GenderPolicy.MALE)
            .phone("0987654321")
            .residentStatus(ResidentStatus.ACTIVE)
            .build();

    contract =
        Contract.builder()
            .id(contractId)
            .contractCode("HD-2026-0187")
            .tenant(tenant)
            .bed(bed)
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 9, 1))
            .monthlyRent(new BigDecimal("1500000.00"))
            .depositAmount(new BigDecimal("1500000.00"))
            .status(ContractStatus.ACTIVE)
            .documents(new ArrayList<>())
            .createdAt(Instant.now())
            .build();

    vehicle =
        Vehicle.builder()
            .id(UUID.randomUUID())
            .tenant(tenant)
            .vehicleType(VehicleType.SCOOTER)
            .licensePlate("59-X3 789.12")
            .brand("Honda")
            .color("Đen")
            .cardCode("THE-0187")
            .status(VehicleStatus.ACTIVE)
            .build();
  }

  @Test
  @DisplayName("getContracts: should return paged summary responses")
  void getContracts_shouldReturnPagedSummaries() {
    Pageable pageable = PageRequest.of(0, 10);
    when(contractRepository.findContractsWithFilters(
            eq("HD-2026"), eq(ContractStatus.ACTIVE), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(contract), pageable, 1));

    Page<ContractSummaryResponse> page =
        contractService.getContracts(pageable, "  HD-2026  ", ContractStatus.ACTIVE);

    assertThat(page.getTotalElements()).isEqualTo(1);
    ContractSummaryResponse summary = page.getContent().getFirst();
    assertThat(summary.contractCode()).isEqualTo("HD-2026-0187");
    assertThat(summary.tenantName()).isEqualTo("Nguyễn Hoàng Nam");
    assertThat(summary.roomNumber()).isEqualTo("P.302");
  }

  @Test
  @DisplayName(
      "renewContract: when valid should update endDate, attach annex document, and set status ACTIVE")
  void renewContract_whenValid_shouldUpdateEndDateAndAttachDoc() {
    RenewContractRequest request =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2028, 9, 1)).build();
    MockMultipartFile annexDoc =
        new MockMultipartFile(
            "annexDocuments", "phu-luc.pdf", "application/pdf", new byte[] {1, 2, 3});

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(storageService.uploadFile(annexDoc, "contracts"))
        .thenReturn("/uploads/contracts/phu-luc.pdf");
    when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

    ContractDetailResponse res =
        contractService.renewContract(contractId, request, List.of(annexDoc));

    assertThat(res.endDate()).isEqualTo(LocalDate.of(2028, 9, 1));
    assertThat(res.status()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(contract.getDocuments()).hasSize(1);
    assertThat(contract.getDocuments().getFirst().getDocumentCategory())
        .isEqualTo(ContractDocumentCategory.RENEWAL_ANNEX);
  }

  @Test
  @DisplayName(
      "renewContract: when newEndDate is before current endDate should throw INVALID_CONTRACT_DATES")
  void renewContract_whenNewEndDateBeforeCurrent_shouldThrow() {
    RenewContractRequest request =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2026, 8, 1)).build();

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

    assertThatThrownBy(() -> contractService.renewContract(contractId, request, null))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode() == ContractErrorCode.INVALID_CONTRACT_DATES);
  }

  @Test
  @DisplayName(
      "terminateContract (Checkout): should cascade to Bed, Room, Tenant, and Vehicle in 1 transaction")
  void terminateContract_shouldCascadeToBedTenantAndVehicle() {
    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(vehicleRepository.findByTenantIdAndDeletedAtIsNull(tenant.getId()))
        .thenReturn(List.of(vehicle));
    when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

    ContractDetailResponse res = contractService.terminateContract(contractId);

    assertThat(res.status()).isEqualTo(ContractStatus.TERMINATED);
    assertThat(contract.getStatus()).isEqualTo(ContractStatus.TERMINATED);
    assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
    assertThat(room.getOccupiedBeds()).isEqualTo(0);
    assertThat(tenant.getResidentStatus()).isEqualTo(ResidentStatus.CHECKED_OUT);
    assertThat(tenant.getDeletedAt()).isNull(); // Soft delete preserves deletedAt = null
    assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.REVOKED);

    verify(bedRepository).save(bed);
    verify(roomRepository).save(room);
    verify(tenantRepository).save(tenant);
    verify(vehicleRepository).save(vehicle);
  }

  @Test
  @DisplayName(
      "terminateContract: when contract already terminated should throw CONTRACT_ALREADY_TERMINATED")
  void terminateContract_whenAlreadyTerminated_shouldThrow() {
    contract.setStatus(ContractStatus.TERMINATED);
    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

    assertThatThrownBy(() -> contractService.terminateContract(contractId))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.CONTRACT_ALREADY_TERMINATED);
  }

  @Test
  @DisplayName("uploadDocuments: when file size > 10MB should throw DOCUMENT_FILE_SIZE_EXCEEDED")
  void uploadDocuments_whenFileSizeExceeded_shouldThrow() {
    MockMultipartFile hugeFile =
        new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[11 * 1024 * 1024]);

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

    assertThatThrownBy(
            () ->
                contractService.uploadDocuments(
                    contractId, ContractDocumentCategory.ORIGINAL_CONTRACT, List.of(hugeFile)))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex ->
                ((BaseAppException) ex).getErrorCode()
                    == ContractErrorCode.DOCUMENT_FILE_SIZE_EXCEEDED);
  }

  @Test
  @DisplayName("uploadDocuments: when valid files should upload and save ContractDocument records")
  void uploadDocuments_whenValid_shouldSaveDocs() {
    MockMultipartFile file1 =
        new MockMultipartFile("files", "page1.jpg", "image/jpeg", new byte[] {1, 2});
    MockMultipartFile file2 =
        new MockMultipartFile("files", "page2.png", "image/png", new byte[] {3, 4});

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(storageService.uploadFile(file1, "contracts")).thenReturn("/uploads/contracts/page1.jpg");
    when(storageService.uploadFile(file2, "contracts")).thenReturn("/uploads/contracts/page2.png");
    when(contractDocumentRepository.save(any(ContractDocument.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    List<ContractDocumentResponse> docs =
        contractService.uploadDocuments(
            contractId, ContractDocumentCategory.ORIGINAL_CONTRACT, List.of(file1, file2));

    assertThat(docs).hasSize(2);
    assertThat(docs.getFirst().fileUrl()).isEqualTo("/uploads/contracts/page1.jpg");
    assertThat(docs.get(1).fileUrl()).isEqualTo("/uploads/contracts/page2.png");
  }

  @Test
  @DisplayName("deleteDocument: should delete file from storage and repository")
  void deleteDocument_shouldDeleteFromStorageAndRepo() {
    UUID docId = UUID.randomUUID();
    ContractDocument doc =
        ContractDocument.builder()
            .id(docId)
            .contract(contract)
            .fileName("page1.jpg")
            .fileUrl("/uploads/contracts/page1.jpg")
            .build();

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

    contractService.deleteDocument(contractId, docId);

    verify(storageService).deleteFile("/uploads/contracts/page1.jpg");
    verify(contractDocumentRepository).delete(doc);
  }
}
