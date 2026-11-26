package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.dtos.request.contract.RenewContractRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentContent;
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
import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.SystemErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractDocumentRepository;
import com.uit.dormmanagement.repositories.ContractRenewalRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.impl.ContractServiceImpl;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ContractServiceImplTest {

  @Mock private ContractRepository contractRepository;
  @Mock private ContractRenewalRepository contractRenewalRepository;
  @Mock private ContractDocumentRepository contractDocumentRepository;
  @Mock private BedRepository bedRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private VehicleRepository vehicleRepository;
  @Mock private PrivateObjectStorage privateObjectStorage;

  @InjectMocks private ContractServiceImpl contractService;

  private UUID contractId;
  private UUID tenantUserId;
  private Contract contract;
  private Room room;
  private Bed bed;
  private Tenant tenant;
  private Vehicle vehicle;

  @BeforeEach
  void setUp() {
    contractId = UUID.randomUUID();
    tenantUserId = UUID.randomUUID();

    room = Room.builder().id(UUID.randomUUID()).roomNumber("P.302").build();
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
            .roomCodeSnapshot("P.401")
            .bedCodeSnapshot("G1")
            .startOn(LocalDate.of(2026, 9, 1))
            .originalEndOn(LocalDate.of(2027, 9, 1))
            .originalMonthlyRent(new BigDecimal("1500000.00"))
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
            .status(VehicleStatus.ISSUED)
            .build();
  }

  @AfterEach
  void cleanUpTransactionSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
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
      "renewContract: when valid should update endDate, attach annex document with private key, and set status ACTIVE")
  void renewContract_whenValid_shouldUpdateEndDateAndAttachDoc() {
    RenewContractRequest request =
        RenewContractRequest.builder().newEndDate(LocalDate.of(2028, 9, 1)).build();
    MockMultipartFile annexDoc =
        new MockMultipartFile(
            "annexDocuments", "phu-luc.pdf", "application/pdf", new byte[] {1, 2, 3});

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractRenewalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(contractDocumentRepository.save(any(ContractDocument.class)))
        .thenAnswer(
            inv -> {
              ContractDocument d = inv.getArgument(0);
              if (d.getId() == null) {
                d.setId(UUID.randomUUID());
              }
              return d;
            });
    when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

    ContractDetailResponse res =
        contractService.renewContract(contractId, request, List.of(annexDoc));

    assertThat(res.endDate()).isEqualTo(LocalDate.of(2028, 9, 1));
    assertThat(res.status()).isEqualTo(ContractStatus.ACTIVE);
    assertThat(contract.getDocuments()).hasSize(1);
    ContractDocument savedDoc = contract.getDocuments().getFirst();
    assertThat(savedDoc.getDocumentCategory()).isEqualTo(ContractDocumentCategory.RENEWAL_ANNEX);
    assertThat(savedDoc.getStorageFileKey()).startsWith("contracts/" + contractId + "/");
    assertThat(savedDoc.getStorageFileKey()).doesNotContain("/uploads/");
    assertThat(res.documents().getFirst().fileUrl())
        .isEqualTo(
            "/api/v1/admin/contracts/"
                + contractId
                + "/documents/"
                + savedDoc.getId()
                + "/content");
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
    when(vehicleRepository.findByTenantId(tenant.getId())).thenReturn(List.of(vehicle));
    when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

    ContractDetailResponse res = contractService.terminateContract(contractId);

    assertThat(res.status()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(contract.getStatus()).isEqualTo(ContractStatus.LIQUIDATED);
    assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
    assertThat(tenant.getResidentStatus()).isEqualTo(ResidentStatus.CHECKED_OUT);
    assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.REVOKED);

    verify(bedRepository).save(bed);
    verify(tenantRepository).save(tenant);
    verify(vehicleRepository).save(vehicle);
  }

  @Test
  @DisplayName(
      "terminateContract: when contract already terminated should throw CONTRACT_ALREADY_TERMINATED")
  void terminateContract_whenAlreadyTerminated_shouldThrow() {
    contract.setStatus(ContractStatus.LIQUIDATED);
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
  @DisplayName(
      "uploadDocuments: stores private object keys and returns authorized admin streaming URLs")
  void uploadDocuments_whenValid_shouldSaveDocsAndReturnStreamingUrls() {
    MockMultipartFile file1 =
        new MockMultipartFile("files", "page1.jpg", "image/jpeg", new byte[] {1, 2});
    MockMultipartFile file2 =
        new MockMultipartFile("files", "page2.png", "image/png", new byte[] {3, 4});

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractDocumentRepository.save(any(ContractDocument.class)))
        .thenAnswer(
            inv -> {
              ContractDocument d = inv.getArgument(0);
              if (d.getId() == null) {
                d.setId(UUID.randomUUID());
              }
              return d;
            });

    List<ContractDocumentResponse> docs =
        contractService.uploadDocuments(
            contractId, ContractDocumentCategory.ORIGINAL_CONTRACT, List.of(file1, file2));

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(privateObjectStorage)
        .putObject(keyCaptor.capture(), eq("image/jpeg"), any(InputStream.class), eq(2L));
    verify(privateObjectStorage)
        .putObject(keyCaptor.capture(), eq("image/png"), any(InputStream.class), eq(2L));

    assertThat(docs).hasSize(2);
    assertThat(docs.getFirst().fileUrl())
        .isEqualTo(
            "/api/v1/admin/contracts/"
                + contractId
                + "/documents/"
                + docs.getFirst().id()
                + "/content");
    assertThat(docs.get(1).fileUrl())
        .isEqualTo(
            "/api/v1/admin/contracts/"
                + contractId
                + "/documents/"
                + docs.get(1).id()
                + "/content");
    assertThat(docs.getFirst().fileUrl())
        .doesNotContain("/uploads/")
        .doesNotContain(keyCaptor.getAllValues().getFirst());
    assertThat(keyCaptor.getAllValues().getFirst()).startsWith("contracts/" + contractId + "/");
  }

  @Test
  @DisplayName("uploadDocuments: cleans up previously uploaded object when a subsequent file fails")
  void uploadDocuments_whenPartialUploadFails_shouldCleanupUploadedObject() {
    MockMultipartFile file1 =
        new MockMultipartFile("files", "page1.jpg", "image/jpeg", new byte[] {1, 2});
    MockMultipartFile file2 =
        new MockMultipartFile("files", "page2.png", "image/png", new byte[] {3, 4});

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractDocumentRepository.save(any(ContractDocument.class)))
        .thenAnswer(
            inv -> {
              ContractDocument d = inv.getArgument(0);
              if (d.getId() == null) {
                d.setId(UUID.randomUUID());
              }
              return d;
            });

    doNothing()
        .when(privateObjectStorage)
        .putObject(any(), eq("image/jpeg"), any(InputStream.class), anyLong());
    doThrow(new BaseAppException(SystemErrorCode.EXTERNAL_SERVICE_ERROR))
        .when(privateObjectStorage)
        .putObject(any(), eq("image/png"), any(InputStream.class), anyLong());

    assertThatThrownBy(
            () ->
                contractService.uploadDocuments(
                    contractId, ContractDocumentCategory.ORIGINAL_CONTRACT, List.of(file1, file2)))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == SystemErrorCode.EXTERNAL_SERVICE_ERROR);

    ArgumentCaptor<String> putKeyCaptor = ArgumentCaptor.forClass(String.class);
    verify(privateObjectStorage)
        .putObject(putKeyCaptor.capture(), eq("image/jpeg"), any(InputStream.class), eq(2L));
    verify(privateObjectStorage).deleteObject(putKeyCaptor.getValue());
  }

  @Test
  @DisplayName("deleteDocument: deletes directly when no transaction synchronization is active")
  void deleteDocument_withoutTxSync_shouldDeleteDirectly() {
    UUID docId = UUID.randomUUID();
    String key = "contracts/" + contractId + "/doc-1.jpg";
    ContractDocument doc =
        ContractDocument.builder()
            .id(docId)
            .contract(contract)
            .originalFilename("page1.jpg")
            .storageFileKey(key)
            .mimeType("image/jpeg")
            .build();
    contract.getDocuments().add(doc);

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

    contractService.deleteDocument(contractId, docId);

    verify(contractDocumentRepository).delete(doc);
    verify(contractDocumentRepository).flush();
    verify(privateObjectStorage).deleteObject(key);
  }

  @Test
  @DisplayName("deleteDocument: defers storage deletion until afterCommit when tx sync is active")
  void deleteDocument_withTxSync_shouldDeleteOnlyAfterCommit() {
    UUID docId = UUID.randomUUID();
    String key = "contracts/" + contractId + "/doc-tx.pdf";
    ContractDocument doc =
        ContractDocument.builder()
            .id(docId)
            .contract(contract)
            .originalFilename("doc-tx.pdf")
            .storageFileKey(key)
            .mimeType("application/pdf")
            .build();
    contract.getDocuments().add(doc);

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));

    TransactionSynchronizationManager.initSynchronization();
    contractService.deleteDocument(contractId, docId);

    verify(contractDocumentRepository).delete(doc);
    verify(privateObjectStorage, never()).deleteObject(any());

    List<TransactionSynchronization> syncs =
        TransactionSynchronizationManager.getSynchronizations();
    assertThat(syncs).hasSize(1);
    syncs.getFirst().afterCommit();
    verify(privateObjectStorage).deleteObject(key);
  }

  @Test
  @DisplayName("getMyContract: exposes tenant-scoped streaming URL and never raw storage key")
  void getMyContract_shouldExposeTenantStreamingUrls() {
    UUID docId = UUID.randomUUID();
    String rawKey = "contracts/" + contractId + "/secret-uuid_contract.pdf";
    ContractDocument doc =
        ContractDocument.builder()
            .id(docId)
            .contract(contract)
            .originalFilename("contract.pdf")
            .storageFileKey(rawKey)
            .mimeType("application/pdf")
            .documentType(ContractDocumentCategory.ORIGINAL_CONTRACT)
            .build();
    contract.getDocuments().add(doc);

    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(contractRepository.findFirstByTenantIdAndStatusIn(eq(tenant.getId()), any()))
        .thenReturn(Optional.of(contract));

    ContractDetailResponse response = contractService.getMyContract(tenantUserId);

    assertThat(response.documents()).hasSize(1);
    assertThat(response.documents().getFirst().fileUrl())
        .isEqualTo("/api/v1/tenant/contracts/" + contractId + "/documents/" + docId + "/content")
        .doesNotContain(rawKey);
  }

  @Test
  @DisplayName(
      "loadDocumentContentForAdmin: streams owned document and rejects cross-contract/missing")
  void loadDocumentContentForAdmin_shouldVerifyContractAndStream() {
    UUID docId = UUID.randomUUID();
    String key = "contracts/" + contractId + "/file.pdf";
    ContractDocument doc =
        ContractDocument.builder()
            .id(docId)
            .contract(contract)
            .originalFilename("file.pdf")
            .storageFileKey(key)
            .mimeType("application/pdf")
            .build();
    StoredObjectContent stored =
        new StoredObjectContent(new ByteArrayResource(new byte[] {1, 2, 3}), "application/pdf", 3);

    when(contractDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(privateObjectStorage.getObject(key)).thenReturn(stored);

    ContractDocumentContent content =
        contractService.loadDocumentContentForAdmin(contractId, docId);
    assertThat(content.contentType()).isEqualTo("application/pdf");
    assertThat(content.contentLength()).isEqualTo(3);
    assertThat(content.originalFilename()).isEqualTo("file.pdf");

    assertThatThrownBy(() -> contractService.loadDocumentContentForAdmin(UUID.randomUUID(), docId))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.DOCUMENT_NOT_FOUND);

    when(privateObjectStorage.getObject(key))
        .thenThrow(new ObjectStorageNotFoundException("missing"));
    assertThatThrownBy(() -> contractService.loadDocumentContentForAdmin(contractId, docId))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.DOCUMENT_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "loadDocumentContentForTenant: allows CHECKED_OUT owner and returns 404 for non-owner")
  void loadDocumentContentForTenant_shouldAllowCheckedOutOwnerAndHideNonOwner() {
    tenant.setResidentStatus(ResidentStatus.CHECKED_OUT);
    contract.setStatus(ContractStatus.LIQUIDATED);
    UUID docId = UUID.randomUUID();
    String key = "contracts/" + contractId + "/checkedout.pdf";
    ContractDocument doc =
        ContractDocument.builder()
            .id(docId)
            .contract(contract)
            .originalFilename("checkedout.pdf")
            .storageFileKey(key)
            .mimeType("application/pdf")
            .build();
    StoredObjectContent stored =
        new StoredObjectContent(new ByteArrayResource(new byte[] {7, 8}), "application/pdf", 2);

    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(contractDocumentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(privateObjectStorage.getObject(key)).thenReturn(stored);

    ContractDocumentContent content =
        contractService.loadDocumentContentForTenant(tenantUserId, contractId, docId);
    assertThat(content.contentLength()).isEqualTo(2);

    UUID otherUserId = UUID.randomUUID();
    Tenant otherTenant = Tenant.builder().id(UUID.randomUUID()).build();
    when(tenantRepository.findByUserId(otherUserId)).thenReturn(Optional.of(otherTenant));

    assertThatThrownBy(
            () -> contractService.loadDocumentContentForTenant(otherUserId, contractId, docId))
        .isInstanceOf(BaseAppException.class)
        .matches(
            ex -> ((BaseAppException) ex).getErrorCode() == ContractErrorCode.CONTRACT_NOT_FOUND);
  }
}
