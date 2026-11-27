package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceDetailResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceProofContent;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceSummaryResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceSummaryStatsResponse;
import com.uit.dormmanagement.dtos.response.invoice.VietQrInfoResponse;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.PaymentTransaction;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.enums.PaymentMethod;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.exceptions.error.InvoiceErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.PaymentTransactionRepository;
import com.uit.dormmanagement.repositories.StaffProfileRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.services.impl.InvoiceServiceImpl;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-10-10T03:00:00Z"); // 2026-10-10 +07
  private static final byte[] VALID_JPEG_BYTES =
      new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
  private static final byte[] VALID_PNG_BYTES =
      new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
  private static final byte[] VALID_WEBP_BYTES =
      new byte[] {
        'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
      };

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private PaymentTransactionRepository paymentTransactionRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private StaffProfileRepository staffProfileRepository;
  @Mock private PrivateObjectStorage privateObjectStorage;
  @Mock private VietQrPayloadService vietQrPayloadService;

  private InvoiceServiceImpl invoiceService;

  private UUID tenantUserId;
  private UUID tenantId;
  private UUID contractId;
  private UUID invoiceId;
  private UUID managerUserId;
  private UUID managerStaffId;
  private User user;
  private Tenant tenant;
  private Contract contract;
  private Invoice invoice;
  private User managerUser;
  private StaffProfile managerStaff;

  @BeforeEach
  void setUp() {
    invoiceService = createService(Clock.fixed(FIXED_NOW, TimeConfig.DORM_ZONE));

    tenantUserId = UUID.randomUUID();
    tenantId = UUID.randomUUID();
    contractId = UUID.randomUUID();
    invoiceId = UUID.randomUUID();
    managerUserId = UUID.randomUUID();
    managerStaffId = UUID.randomUUID();

    user = User.builder().id(tenantUserId).email("tenant.proof@uit.edu.vn").build();
    managerUser =
        User.builder()
            .id(managerUserId)
            .email("manager.billing@uit.edu.vn")
            .role(UserRole.MANAGER)
            .build();
    managerStaff =
        StaffProfile.builder()
            .id(managerStaffId)
            .user(managerUser)
            .fullName("Tran Quan Ly")
            .phone("0909000111")
            .build();
    tenant =
        Tenant.builder()
            .id(tenantId)
            .user(user)
            .tenantCode("NT-PROOF-01")
            .fullName("Nguyen Van Proof")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079203001122")
            .phone("0901234567")
            .permanentAddress("Thu Duc, TP.HCM")
            .residentStatus(ResidentStatus.ACTIVE)
            .build();
    contract =
        Contract.builder()
            .id(contractId)
            .contractCode("HD-2026-0099")
            .tenant(tenant)
            .roomCodeSnapshot("P.401")
            .bedCodeSnapshot("G01")
            .startOn(LocalDate.of(2026, 9, 1))
            .originalEndOn(LocalDate.of(2027, 2, 28))
            .originalMonthlyRent(new BigDecimal("1500000"))
            .depositAmount(new BigDecimal("1500000"))
            .status(ContractStatus.ACTIVE)
            .build();
    invoice =
        Invoice.builder()
            .id(invoiceId)
            .invoiceCode("INV-202610-HD-2026-0099")
            .contract(contract)
            .billingPeriod(LocalDate.of(2026, 10, 1))
            .amount(new BigDecimal("1500000"))
            .dueOn(LocalDate.of(2026, 10, 5))
            .status(InvoiceStatus.DEBT)
            .issuedAt(Instant.parse("2026-09-25T00:00:05Z"))
            .build();
  }

  @AfterEach
  void cleanUpTxSync() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private InvoiceServiceImpl createService(Clock clock) {
    return new InvoiceServiceImpl(
        invoiceRepository,
        paymentTransactionRepository,
        tenantRepository,
        staffProfileRepository,
        privateObjectStorage,
        vietQrPayloadService,
        clock);
  }

  @Test
  @DisplayName(
      "submitProof: accepts JPG, JPEG, PNG, WEBP (including 5 MB boundary), sets PENDING_CONFIRMATION without PaymentTransaction, and returns tenant URL")
  void submitProof_validFormatsAndBoundarySize_transitionsToPendingConfirmation() {
    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    when(paymentTransactionRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());
    when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

    byte[] exactFiveMbPng = new byte[5 * 1024 * 1024];
    System.arraycopy(VALID_PNG_BYTES, 0, exactFiveMbPng, 0, VALID_PNG_BYTES.length);

    List<MockMultipartFile> validFiles =
        List.of(
            new MockMultipartFile("file", "bill.jpg", "image/jpeg", VALID_JPEG_BYTES),
            new MockMultipartFile(
                "file", "C:\\fakepath\\receipt.JPEG", "image/pjpeg", VALID_JPEG_BYTES),
            new MockMultipartFile("file", "proof.png", "image/png", exactFiveMbPng),
            new MockMultipartFile(
                "file", "scan.webp", "application/octet-stream", VALID_WEBP_BYTES));
    List<String> expectedMimes = List.of("image/jpeg", "image/jpeg", "image/png", "image/webp");
    List<String> expectedExts = List.of(".jpg", ".jpeg", ".png", ".webp");

    for (int i = 0; i < validFiles.size(); i++) {
      invoice.setStatus(InvoiceStatus.DEBT);
      invoice.setProofFileKey(null);
      invoice.setProofSubmittedAt(null);

      MockMultipartFile file = validFiles.get(i);
      InvoiceDetailResponse response = invoiceService.submitProof(tenantUserId, invoiceId, file);

      assertThat(response.status()).isEqualTo(InvoiceStatus.PENDING_CONFIRMATION);
      assertThat(response.proofAvailable()).isTrue();
      assertThat(response.proofSubmittedAt()).isEqualTo(FIXED_NOW);
      assertThat(response.overdue()).isFalse();
      assertThat(response.overdueDays()).isZero();
      assertThat(response.vietQr()).isNull();
      assertThat(response.payment()).isNull();
      assertThat(response.proof()).isNotNull();
      assertThat(response.proof().available()).isTrue();
      assertThat(response.proof().submittedAt()).isEqualTo(FIXED_NOW);
      assertThat(response.proof().contentUrl())
          .isEqualTo("/api/v1/tenant/invoices/" + invoiceId + "/proof/content")
          .doesNotContain(invoice.getProofFileKey());

      assertThat(invoice.getProofFileKey())
          .startsWith("invoices/" + invoiceId + "/proof/")
          .endsWith(expectedExts.get(i));
      assertThat(invoice.getProofSubmittedAt()).isEqualTo(FIXED_NOW);

      verify(privateObjectStorage)
          .putObject(
              eq(invoice.getProofFileKey()),
              eq(expectedMimes.get(i)),
              any(InputStream.class),
              eq(file.getSize()));
    }

    verify(paymentTransactionRepository, never()).save(any());
    verify(paymentTransactionRepository, never()).saveAndFlush(any());
    verifyNoInteractions(vietQrPayloadService);
  }

  @Test
  @DisplayName("submitProof: works for BOTH ACTIVE and CHECKED_OUT tenants")
  void submitProof_worksForActiveAndCheckedOutTenants() {
    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    when(paymentTransactionRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());
    when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

    MockMultipartFile png =
        new MockMultipartFile("file", "active.png", "image/png", VALID_PNG_BYTES);
    InvoiceDetailResponse activeRes = invoiceService.submitProof(tenantUserId, invoiceId, png);
    assertThat(activeRes.status()).isEqualTo(InvoiceStatus.PENDING_CONFIRMATION);

    // Reset invoice to DEBT and transition tenant/contract to CHECKED_OUT / LIQUIDATED
    tenant.setResidentStatus(ResidentStatus.CHECKED_OUT);
    contract.setStatus(ContractStatus.LIQUIDATED);
    contract.setCheckedOutAt(Instant.parse("2026-10-08T10:00:00Z"));
    invoice.setStatus(InvoiceStatus.DEBT);
    invoice.setProofFileKey(null);
    invoice.setProofSubmittedAt(null);

    MockMultipartFile jpg =
        new MockMultipartFile("file", "checked-out.jpg", "image/jpeg", VALID_JPEG_BYTES);
    InvoiceDetailResponse checkedOutRes = invoiceService.submitProof(tenantUserId, invoiceId, jpg);
    assertThat(checkedOutRes.status()).isEqualTo(InvoiceStatus.PENDING_CONFIRMATION);
    assertThat(checkedOutRes.proof()).isNotNull();
    assertThat(checkedOutRes.proof().contentUrl())
        .isEqualTo("/api/v1/tenant/invoices/" + invoiceId + "/proof/content");
  }

  @Test
  @DisplayName(
      "submitProof: rejects null/empty file (PROOF_FILE_REQUIRED) and >5MB file (PROOF_FILE_SIZE_EXCEEDED) before touching DB or storage")
  void submitProof_rejectsMissingEmptyAndOversizedFiles() {
    assertThatThrownBy(() -> invoiceService.submitProof(tenantUserId, invoiceId, null))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.PROOF_FILE_REQUIRED);

    MockMultipartFile emptyFile =
        new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
    assertThatThrownBy(() -> invoiceService.submitProof(tenantUserId, invoiceId, emptyFile))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.PROOF_FILE_REQUIRED);

    byte[] oversizedBytes = new byte[5 * 1024 * 1024 + 1];
    System.arraycopy(VALID_PNG_BYTES, 0, oversizedBytes, 0, VALID_PNG_BYTES.length);
    MockMultipartFile oversized =
        new MockMultipartFile("file", "too-big.png", "image/png", oversizedBytes);
    assertThatThrownBy(() -> invoiceService.submitProof(tenantUserId, invoiceId, oversized))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.PROOF_FILE_SIZE_EXCEEDED);

    verifyNoInteractions(
        tenantRepository,
        invoiceRepository,
        paymentTransactionRepository,
        privateObjectStorage,
        vietQrPayloadService);
  }

  @Test
  @DisplayName(
      "submitProof: rejects unsupported extensions, mismatched Content-Type, and spoofed/truncated magic bytes before DB or storage")
  void submitProof_rejectsInvalidExtensionsMismatchedMimeAndSpoofedMagicBytes() {
    byte[] htmlBytes = "<html>not-an-image</html>".getBytes(StandardCharsets.UTF_8);
    byte[] riffAviBytes =
        new byte[] {'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'A', 'V', 'I', ' '};

    List<MultipartFile> invalidFiles =
        List.of(
            new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8)),
            new MockMultipartFile(
                "file", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)),
            new MockMultipartFile(
                "file", "anim.gif", "image/gif", "GIF89a".getBytes(StandardCharsets.UTF_8)),
            new MockMultipartFile("file", "noext", "image/png", VALID_PNG_BYTES),
            new MockMultipartFile("file", ".png", "image/png", VALID_PNG_BYTES),
            new MockMultipartFile("file", "   ", "image/png", VALID_PNG_BYTES),
            new MockMultipartFile("file", "mismatch.jpg", "image/png", VALID_JPEG_BYTES),
            new MockMultipartFile("file", "mismatch.png", "text/html", VALID_PNG_BYTES),
            new MockMultipartFile("file", "mismatch.webp", "image/jpeg", VALID_WEBP_BYTES),
            new MockMultipartFile("file", "spoof.jpg", "image/jpeg", htmlBytes),
            new MockMultipartFile("file", "cross.jpg", "image/jpeg", VALID_PNG_BYTES),
            new MockMultipartFile(
                "file", "short.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8}),
            new MockMultipartFile("file", "spoof.png", "image/png", htmlBytes),
            new MockMultipartFile(
                "file",
                "short.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A}),
            new MockMultipartFile("file", "spoof.webp", "image/webp", riffAviBytes),
            new MockMultipartFile(
                "file",
                "short.webp",
                "image/webp",
                new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B'}));

    for (MultipartFile badFile : invalidFiles) {
      assertThatThrownBy(() -> invoiceService.submitProof(tenantUserId, invoiceId, badFile))
          .as("Expected UNSUPPORTED_PROOF_FORMAT for %s", badFile.getOriginalFilename())
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.UNSUPPORTED_PROOF_FORMAT);
    }

    verifyNoInteractions(
        tenantRepository,
        invoiceRepository,
        paymentTransactionRepository,
        privateObjectStorage,
        vietQrPayloadService);
  }

  @Test
  @DisplayName(
      "submitProof: enforces missing tenant/invoice/non-owner 404 isolation and PENDING_CONFIRMATION/PAID 409 state rules")
  void submitProof_enforcesOwnershipAndStateTransitions() {
    MockMultipartFile png =
        new MockMultipartFile("file", "proof.png", "image/png", VALID_PNG_BYTES);

    // Missing tenant -> 404
    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> invoiceService.submitProof(tenantUserId, invoiceId, png))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);

    // Missing invoice -> 404
    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> invoiceService.submitProof(tenantUserId, invoiceId, png))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);

    // Non-owner -> 404
    UUID otherUserId = UUID.randomUUID();
    Tenant otherTenant = Tenant.builder().id(UUID.randomUUID()).build();
    when(tenantRepository.findByUserId(otherUserId)).thenReturn(Optional.of(otherTenant));
    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    assertThatThrownBy(() -> invoiceService.submitProof(otherUserId, invoiceId, png))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);

    // PENDING_CONFIRMATION -> 409 INVOICE_409_NOT_DEBT
    invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    when(paymentTransactionRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> invoiceService.submitProof(tenantUserId, invoiceId, png))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_DEBT);

    // PAID status -> 409 INVOICE_409_ALREADY_PAID
    invoice.setStatus(InvoiceStatus.PAID);
    assertThatThrownBy(() -> invoiceService.submitProof(tenantUserId, invoiceId, png))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);

    // Existing PaymentTransaction row even with DEBT status -> 409 INVOICE_409_ALREADY_PAID
    invoice.setStatus(InvoiceStatus.DEBT);
    when(paymentTransactionRepository.findByInvoiceId(invoiceId))
        .thenReturn(Optional.of(PaymentTransaction.builder().id(UUID.randomUUID()).build()));
    assertThatThrownBy(() -> invoiceService.submitProof(tenantUserId, invoiceId, png))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);

    verifyNoInteractions(privateObjectStorage);
  }

  @Test
  @DisplayName(
      "submitProof: deletes newly uploaded object when DB save throws or tx rolls back, and deletes stale previous key only after commit")
  void submitProof_cleansUpUploadedObjectOnFailureOrRollback_andDeletesPreviousKeyAfterCommit() {
    MockMultipartFile png =
        new MockMultipartFile("file", "proof.png", "image/png", VALID_PNG_BYTES);

    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    when(paymentTransactionRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());

    // 1. DB saveAndFlush throws -> immediate storage cleanup
    doNothing().when(privateObjectStorage).putObject(any(), any(), any(), anyLong());
    when(invoiceRepository.saveAndFlush(any(Invoice.class)))
        .thenThrow(new IllegalStateException("Simulated DB failure"));

    assertThatThrownBy(() -> invoiceService.submitProof(tenantUserId, invoiceId, png))
        .isInstanceOf(IllegalStateException.class);

    ArgumentCaptor<String> failedKeyCaptor = ArgumentCaptor.forClass(String.class);
    verify(privateObjectStorage).deleteObject(failedKeyCaptor.capture());
    assertThat(failedKeyCaptor.getValue()).startsWith("invoices/" + invoiceId + "/proof/");

    // 2. Active TransactionSynchronization rollback -> afterCompletion(STATUS_ROLLED_BACK) deletes
    // uploaded object
    invoice.setStatus(InvoiceStatus.DEBT);
    invoice.setProofFileKey(null);
    invoice.setProofSubmittedAt(null);
    lenient()
        .when(invoiceRepository.saveAndFlush(any(Invoice.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    TransactionSynchronizationManager.initSynchronization();
    invoiceService.submitProof(tenantUserId, invoiceId, png);
    String rollbackKey = invoice.getProofFileKey();
    for (TransactionSynchronization sync :
        TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    }
    verify(privateObjectStorage).deleteObject(rollbackKey);
    TransactionSynchronizationManager.clearSynchronization();

    // 3. Active TransactionSynchronization commit with stale previousProofKey -> keeps new key and
    // deletes old key on afterCommit
    String staleOldKey = "invoices/" + invoiceId + "/proof/stale-old.jpg";
    invoice.setStatus(InvoiceStatus.DEBT);
    invoice.setProofFileKey(staleOldKey);
    invoice.setProofSubmittedAt(null);

    TransactionSynchronizationManager.initSynchronization();
    invoiceService.submitProof(tenantUserId, invoiceId, png);
    String committedKey = invoice.getProofFileKey();
    verify(privateObjectStorage, never()).deleteObject(staleOldKey);
    verify(privateObjectStorage, never()).deleteObject(committedKey);

    for (TransactionSynchronization sync :
        TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCommit();
      sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
    }
    verify(privateObjectStorage).deleteObject(staleOldKey);
    verify(privateObjectStorage, never()).deleteObject(committedKey);
  }

  @Test
  @DisplayName(
      "submitProof: wraps IOException during storage upload and deletes uploaded key on storage failure")
  void submitProof_whenInputStreamThrowsIOException_throwsInternalError() throws Exception {
    MultipartFile brokenFile = org.mockito.Mockito.mock(MultipartFile.class);
    when(brokenFile.isEmpty()).thenReturn(false);
    when(brokenFile.getSize()).thenReturn(10L);
    when(brokenFile.getOriginalFilename()).thenReturn("proof.png");
    when(brokenFile.getContentType()).thenReturn("image/png");
    when(brokenFile.getInputStream())
        .thenReturn(new java.io.ByteArrayInputStream(VALID_PNG_BYTES))
        .thenThrow(new IOException("Stream read failed"));

    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    when(paymentTransactionRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> invoiceService.submitProof(tenantUserId, invoiceId, brokenFile))
        .isInstanceOf(BaseAppException.class)
        .hasMessageContaining("Không thể đọc dữ liệu ảnh minh chứng");
    verifyNoInteractions(privateObjectStorage);
  }

  @Test
  @DisplayName(
      "rejectProof: transitions PENDING_CONFIRMATION -> DEBT, clears proof, defers S3 delete to afterCommit, and recomputes overdue & VietQR")
  void rejectProof_transitionsToDebtClearsProofDefersDeleteAndRecomputesOverdueAndVietQr() {
    String oldProofKey = "invoices/" + invoiceId + "/proof/old.png";
    invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    invoice.setProofFileKey(oldProofKey);
    invoice.setProofSubmittedAt(Instant.parse("2026-10-08T09:00:00Z"));
    invoice.setDueOn(LocalDate.of(2026, 10, 5)); // today is 2026-10-10 -> 5 overdue days

    VietQrInfoResponse qrInfo =
        VietQrInfoResponse.builder()
            .bankBin("970405")
            .bankName("Agribank")
            .accountNumber("1900123456789")
            .accountName("KY TUC XA TEST HUB")
            .amount(new BigDecimal("1500000"))
            .transferContent("KTX INV-202610-HD-2026-0099")
            .qrPayload("000201...")
            .build();

    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    when(paymentTransactionRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());
    when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
    when(vietQrPayloadService.buildForInvoice(any(Invoice.class))).thenReturn(Optional.of(qrInfo));

    TransactionSynchronizationManager.initSynchronization();
    InvoiceDetailResponse response = invoiceService.rejectProof(invoiceId);

    assertThat(response.status()).isEqualTo(InvoiceStatus.DEBT);
    assertThat(response.proofAvailable()).isFalse();
    assertThat(response.proofSubmittedAt()).isNull();
    assertThat(response.proof()).isNull();
    assertThat(response.vietQr()).isEqualTo(qrInfo);
    assertThat(response.overdue()).isTrue();
    assertThat(response.overdueDays()).isEqualTo(5L);
    assertThat(response.tenantIdCardNumber()).isEqualTo("079203001122");
    assertThat(response.tenantEmail()).isEqualTo("tenant.proof@uit.edu.vn");

    assertThat(invoice.getProofFileKey()).isNull();
    assertThat(invoice.getProofSubmittedAt()).isNull();

    // S3 delete deferred until afterCommit
    verify(privateObjectStorage, never()).deleteObject(any());
    List<TransactionSynchronization> syncs =
        TransactionSynchronizationManager.getSynchronizations();
    assertThat(syncs).hasSize(1);
    syncs.getFirst().afterCommit();
    verify(privateObjectStorage).deleteObject(oldProofKey);

    TransactionSynchronizationManager.clearSynchronization();

    // Due-date boundary (dueOn == today) -> overdue = false, and deletes directly when no tx sync
    invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    invoice.setProofFileKey("invoices/" + invoiceId + "/proof/due-today.jpg");
    invoice.setProofSubmittedAt(FIXED_NOW);
    invoice.setDueOn(LocalDate.of(2026, 10, 10));

    InvoiceDetailResponse dueTodayRes = invoiceService.rejectProof(invoiceId);
    assertThat(dueTodayRes.overdue()).isFalse();
    assertThat(dueTodayRes.overdueDays()).isZero();
    verify(privateObjectStorage).deleteObject("invoices/" + invoiceId + "/proof/due-today.jpg");
  }

  @Test
  @DisplayName(
      "rejectProof: rejects missing invoice (404), DEBT (409 NOT_PENDING), and PAID (409 ALREADY_PAID)")
  void rejectProof_rejectsMissingAndInvalidState() {
    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> invoiceService.rejectProof(invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);

    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    invoice.setStatus(InvoiceStatus.DEBT);
    when(paymentTransactionRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> invoiceService.rejectProof(invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", InvoiceErrorCode.INVOICE_NOT_PENDING_CONFIRMATION);

    invoice.setStatus(InvoiceStatus.PAID);
    assertThatThrownBy(() -> invoiceService.rejectProof(invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);

    invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    when(paymentTransactionRepository.findByInvoiceId(invoiceId))
        .thenReturn(Optional.of(PaymentTransaction.builder().id(UUID.randomUUID()).build()));
    assertThatThrownBy(() -> invoiceService.rejectProof(invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);

    verifyNoInteractions(privateObjectStorage);
  }

  @Test
  @DisplayName(
      "loadProofContentForAdmin & loadProofContentForTenant: stream with safe synthetic filename, allow CHECKED_OUT owner, and return 404 on cross-tenant or missing proof")
  void loadProofContent_streamsSyntheticFilenameEnforcesOwnershipAndMissingProof() {
    String pngKey = "invoices/" + invoiceId + "/proof/secret-uuid.png";
    invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    invoice.setProofFileKey(pngKey);
    invoice.setProofSubmittedAt(FIXED_NOW);

    StoredObjectContent storedPng =
        new StoredObjectContent(new ByteArrayResource(VALID_PNG_BYTES), "image/png", 9);

    when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
    when(privateObjectStorage.getObject(pngKey)).thenReturn(storedPng);

    InvoiceProofContent adminContent = invoiceService.loadProofContentForAdmin(invoiceId);
    assertThat(adminContent.contentType()).isEqualTo("image/png");
    assertThat(adminContent.contentLength()).isEqualTo(9);
    assertThat(adminContent.downloadFilename()).isEqualTo("INV-202610-HD-2026-0099-proof.png");
    assertThat(adminContent.downloadFilename()).doesNotContain("secret-uuid");

    // Checked-out tenant owner with .jpeg key + octet-stream stored MIME -> normalized .jpg +
    // image/jpeg
    tenant.setResidentStatus(ResidentStatus.CHECKED_OUT);
    String jpegKey = "invoices/" + invoiceId + "/proof/another-secret.jpeg";
    invoice.setProofFileKey(jpegKey);
    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(privateObjectStorage.getObject(jpegKey))
        .thenReturn(
            new StoredObjectContent(
                new ByteArrayResource(VALID_JPEG_BYTES), "application/octet-stream", 6));

    InvoiceProofContent tenantContent =
        invoiceService.loadProofContentForTenant(tenantUserId, invoiceId);
    assertThat(tenantContent.contentType()).isEqualTo("image/jpeg");
    assertThat(tenantContent.contentLength()).isEqualTo(6);
    assertThat(tenantContent.downloadFilename()).isEqualTo("INV-202610-HD-2026-0099-proof.jpg");

    // Cross-tenant -> 404 INVOICE_404
    UUID otherUserId = UUID.randomUUID();
    when(tenantRepository.findByUserId(otherUserId))
        .thenReturn(Optional.of(Tenant.builder().id(UUID.randomUUID()).build()));
    assertThatThrownBy(() -> invoiceService.loadProofContentForTenant(otherUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);

    // Missing proofFileKey -> 404 INVOICE_404_PROOF
    invoice.setProofFileKey(null);
    assertThatThrownBy(() -> invoiceService.loadProofContentForAdmin(invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_PROOF_NOT_FOUND);

    // Missing object in privateObjectStorage -> 404 INVOICE_404_PROOF
    invoice.setProofFileKey(pngKey);
    doThrow(new ObjectStorageNotFoundException("missing"))
        .when(privateObjectStorage)
        .getObject(pngKey);
    assertThatThrownBy(() -> invoiceService.loadProofContentForTenant(tenantUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_PROOF_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "confirmCashPayment: transitions DEBT -> PAID, creates CASH PaymentTransaction with PT- prefix, clears overdue & VietQR")
  void confirmCashPayment_happyPath_marksPaidAndCreatesCashTransaction() {
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));
    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    when(paymentTransactionRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());
    when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
    when(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class)))
        .thenAnswer(
            inv -> {
              PaymentTransaction tx = inv.getArgument(0);
              tx.setId(UUID.randomUUID());
              tx.setConfirmedAt(FIXED_NOW);
              return tx;
            });

    InvoiceDetailResponse response = invoiceService.confirmCashPayment(managerUserId, invoiceId);

    assertThat(response.status()).isEqualTo(InvoiceStatus.PAID);
    assertThat(response.overdue()).isFalse();
    assertThat(response.overdueDays()).isZero();
    assertThat(response.vietQr()).isNull();
    assertThat(response.proofAvailable()).isFalse();
    assertThat(response.proof()).isNull();
    assertThat(response.payment()).isNotNull();
    assertThat(response.payment().id()).isNotNull();
    assertThat(response.payment().receiptCode()).isEqualTo("PT-INV-202610-HD-2026-0099");
    assertThat(response.payment().amount()).isEqualByComparingTo("1500000");
    assertThat(response.payment().paymentMethod()).isEqualTo(PaymentMethod.CASH);
    assertThat(response.payment().confirmedByStaffId()).isEqualTo(managerStaffId);
    assertThat(response.payment().confirmedByStaffName()).isEqualTo("Tran Quan Ly");
    assertThat(response.payment().confirmedAt()).isEqualTo(FIXED_NOW);

    ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
    verify(paymentTransactionRepository).saveAndFlush(txCaptor.capture());
    PaymentTransaction savedTx = txCaptor.getValue();
    assertThat(savedTx.getInvoice()).isSameAs(invoice);
    assertThat(savedTx.getReceiptCode()).isEqualTo("PT-INV-202610-HD-2026-0099");
    assertThat(savedTx.getAmount()).isEqualByComparingTo("1500000");
    assertThat(savedTx.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
    assertThat(savedTx.getConfirmedByStaff()).isSameAs(managerStaff);
    verifyNoInteractions(privateObjectStorage, vietQrPayloadService);
  }

  @Test
  @DisplayName(
      "confirmCashPayment: rejects null userId, missing StaffProfile, or non-MANAGER role with 403 INVOICE_403_STAFF_PROFILE")
  void confirmCashPayment_rejectsInvalidManagerActor() {
    assertThatThrownBy(() -> invoiceService.confirmCashPayment(null, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);

    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> invoiceService.confirmCashPayment(managerUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);

    UUID techUserId = UUID.randomUUID();
    StaffProfile techStaff =
        StaffProfile.builder()
            .id(UUID.randomUUID())
            .user(User.builder().id(techUserId).role(UserRole.TECHNICIAN).build())
            .fullName("Le Ky Thuat")
            .phone("0908000222")
            .build();
    when(staffProfileRepository.findByUserId(techUserId)).thenReturn(Optional.of(techStaff));
    assertThatThrownBy(() -> invoiceService.confirmCashPayment(techUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);

    verifyNoInteractions(invoiceRepository, paymentTransactionRepository);
  }

  @Test
  @DisplayName(
      "confirmCashPayment: rejects missing invoice (404), PENDING_CONFIRMATION (409 NOT_DEBT), and PAID/existing tx (409 ALREADY_PAID)")
  void confirmCashPayment_rejectsMissingAndInvalidStates() {
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));

    assertThatThrownBy(() -> invoiceService.confirmCashPayment(managerUserId, null))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);

    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> invoiceService.confirmCashPayment(managerUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);

    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    when(paymentTransactionRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());

    invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    invoice.setProofFileKey("invoices/" + invoiceId + "/proof/bill.png");
    invoice.setProofSubmittedAt(FIXED_NOW);
    assertThatThrownBy(() -> invoiceService.confirmCashPayment(managerUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_DEBT);

    invoice.setStatus(InvoiceStatus.PAID);
    assertThatThrownBy(() -> invoiceService.confirmCashPayment(managerUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);

    invoice.setStatus(InvoiceStatus.DEBT);
    when(paymentTransactionRepository.findByInvoiceId(invoiceId))
        .thenReturn(Optional.of(PaymentTransaction.builder().id(UUID.randomUUID()).build()));
    assertThatThrownBy(() -> invoiceService.confirmCashPayment(managerUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);

    verify(invoiceRepository, never()).saveAndFlush(any());
    verify(paymentTransactionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName(
      "confirmCashPayment: translates unique constraint race to 409 ALREADY_PAID and rethrows other integrity violations")
  void confirmCashPayment_handlesUniqueConstraintRace() {
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));
    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    when(paymentTransactionRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());
    when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

    for (String constraint :
        List.of("payment_transactions_invoice_id_key", "payment_transactions_receipt_code_key")) {
      invoice.setStatus(InvoiceStatus.DEBT);
      when(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class)))
          .thenThrow(uniqueConstraintViolation(constraint));

      assertThatThrownBy(() -> invoiceService.confirmCashPayment(managerUserId, invoiceId))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);
    }

    invoice.setStatus(InvoiceStatus.DEBT);
    DataIntegrityViolationException checkFailure =
        new DataIntegrityViolationException(
            "check",
            new ConstraintViolationException(
                "check", new SQLException("check failed", "23514"), "ck_payment_amount"));
    when(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class)))
        .thenThrow(checkFailure);

    assertThatThrownBy(() -> invoiceService.confirmCashPayment(managerUserId, invoiceId))
        .isSameAs(checkFailure);
  }

  @Test
  @DisplayName(
      "confirmTransferPayment: transitions PENDING_CONFIRMATION -> PAID, creates VIETQR_TRANSFER tx, preserves proof and admin URL")
  void confirmTransferPayment_happyPath_marksPaidCreatesTransferTxAndPreservesProof() {
    String proofKey = "invoices/" + invoiceId + "/proof/transfer.png";
    Instant submittedAt = Instant.parse("2026-10-08T09:00:00Z");
    invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    invoice.setProofFileKey(proofKey);
    invoice.setProofSubmittedAt(submittedAt);

    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));
    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    when(paymentTransactionRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());
    when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
    when(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class)))
        .thenAnswer(
            inv -> {
              PaymentTransaction tx = inv.getArgument(0);
              tx.setId(UUID.randomUUID());
              tx.setConfirmedAt(FIXED_NOW);
              return tx;
            });

    InvoiceDetailResponse response =
        invoiceService.confirmTransferPayment(managerUserId, invoiceId);

    assertThat(response.status()).isEqualTo(InvoiceStatus.PAID);
    assertThat(response.overdue()).isFalse();
    assertThat(response.overdueDays()).isZero();
    assertThat(response.vietQr()).isNull();
    assertThat(response.proofAvailable()).isTrue();
    assertThat(response.proofSubmittedAt()).isEqualTo(submittedAt);
    assertThat(response.proof()).isNotNull();
    assertThat(response.proof().available()).isTrue();
    assertThat(response.proof().submittedAt()).isEqualTo(submittedAt);
    assertThat(response.proof().contentUrl())
        .isEqualTo("/api/v1/admin/invoices/" + invoiceId + "/proof/content");
    assertThat(response.payment()).isNotNull();
    assertThat(response.payment().id()).isNotNull();
    assertThat(response.payment().receiptCode()).isEqualTo("PT-INV-202610-HD-2026-0099");
    assertThat(response.payment().amount()).isEqualByComparingTo("1500000");
    assertThat(response.payment().paymentMethod()).isEqualTo(PaymentMethod.VIETQR_TRANSFER);
    assertThat(response.payment().confirmedByStaffId()).isEqualTo(managerStaffId);
    assertThat(response.payment().confirmedByStaffName()).isEqualTo("Tran Quan Ly");
    assertThat(response.payment().confirmedAt()).isEqualTo(FIXED_NOW);

    assertThat(invoice.getProofFileKey()).isEqualTo(proofKey);
    assertThat(invoice.getProofSubmittedAt()).isEqualTo(submittedAt);

    ArgumentCaptor<PaymentTransaction> txCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
    verify(paymentTransactionRepository).saveAndFlush(txCaptor.capture());
    PaymentTransaction savedTx = txCaptor.getValue();
    assertThat(savedTx.getPaymentMethod()).isEqualTo(PaymentMethod.VIETQR_TRANSFER);
    assertThat(savedTx.getReceiptCode()).isEqualTo("PT-INV-202610-HD-2026-0099");
    assertThat(savedTx.getAmount()).isEqualByComparingTo("1500000");
    assertThat(savedTx.getConfirmedByStaff()).isSameAs(managerStaff);
    verifyNoInteractions(privateObjectStorage, vietQrPayloadService);
  }

  @Test
  @DisplayName(
      "confirmTransferPayment: rejects null userId, missing StaffProfile, or non-MANAGER role with 403 INVOICE_403_STAFF_PROFILE")
  void confirmTransferPayment_rejectsInvalidManagerActor() {
    assertThatThrownBy(() -> invoiceService.confirmTransferPayment(null, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);

    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> invoiceService.confirmTransferPayment(managerUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);

    UUID techUserId = UUID.randomUUID();
    StaffProfile techStaff =
        StaffProfile.builder()
            .id(UUID.randomUUID())
            .user(User.builder().id(techUserId).role(UserRole.TECHNICIAN).build())
            .fullName("Le Ky Thuat")
            .phone("0908000222")
            .build();
    when(staffProfileRepository.findByUserId(techUserId)).thenReturn(Optional.of(techStaff));
    assertThatThrownBy(() -> invoiceService.confirmTransferPayment(techUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.MANAGER_STAFF_PROFILE_REQUIRED);

    verifyNoInteractions(invoiceRepository, paymentTransactionRepository);
  }

  @Test
  @DisplayName(
      "confirmTransferPayment: rejects missing invoice (404), DEBT or blank proof (409 NOT_PENDING), and PAID/existing tx (409 ALREADY_PAID)")
  void confirmTransferPayment_rejectsMissingInvalidStatesAndMissingProof() {
    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));

    assertThatThrownBy(() -> invoiceService.confirmTransferPayment(managerUserId, null))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);

    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> invoiceService.confirmTransferPayment(managerUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);

    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    when(paymentTransactionRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());

    // DEBT -> 409 INVOICE_409_NOT_PENDING
    invoice.setStatus(InvoiceStatus.DEBT);
    assertThatThrownBy(() -> invoiceService.confirmTransferPayment(managerUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", InvoiceErrorCode.INVOICE_NOT_PENDING_CONFIRMATION);

    // PENDING_CONFIRMATION with null proofFileKey -> 409 INVOICE_409_NOT_PENDING
    invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    invoice.setProofFileKey(null);
    assertThatThrownBy(() -> invoiceService.confirmTransferPayment(managerUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", InvoiceErrorCode.INVOICE_NOT_PENDING_CONFIRMATION);

    // PENDING_CONFIRMATION with blank proofFileKey -> 409 INVOICE_409_NOT_PENDING
    invoice.setProofFileKey("   ");
    assertThatThrownBy(() -> invoiceService.confirmTransferPayment(managerUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", InvoiceErrorCode.INVOICE_NOT_PENDING_CONFIRMATION);

    // PAID status -> 409 INVOICE_409_ALREADY_PAID
    invoice.setStatus(InvoiceStatus.PAID);
    assertThatThrownBy(() -> invoiceService.confirmTransferPayment(managerUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);

    // Existing PaymentTransaction row -> 409 INVOICE_409_ALREADY_PAID
    invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    invoice.setProofFileKey("invoices/" + invoiceId + "/proof/bill.png");
    when(paymentTransactionRepository.findByInvoiceId(invoiceId))
        .thenReturn(Optional.of(PaymentTransaction.builder().id(UUID.randomUUID()).build()));
    assertThatThrownBy(() -> invoiceService.confirmTransferPayment(managerUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);

    verify(invoiceRepository, never()).saveAndFlush(any());
    verify(paymentTransactionRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName(
      "confirmTransferPayment: translates unique constraint race to 409 ALREADY_PAID and rethrows other integrity violations")
  void confirmTransferPayment_handlesUniqueConstraintRace() {
    invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    invoice.setProofFileKey("invoices/" + invoiceId + "/proof/transfer.png");
    invoice.setProofSubmittedAt(FIXED_NOW);

    when(staffProfileRepository.findByUserId(managerUserId)).thenReturn(Optional.of(managerStaff));
    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    when(paymentTransactionRepository.findByInvoiceId(invoiceId)).thenReturn(Optional.empty());
    when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

    for (String constraint :
        List.of("payment_transactions_invoice_id_key", "payment_transactions_receipt_code_key")) {
      invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
      when(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class)))
          .thenThrow(uniqueConstraintViolation(constraint));

      assertThatThrownBy(() -> invoiceService.confirmTransferPayment(managerUserId, invoiceId))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_ALREADY_PAID);
    }

    invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    DataIntegrityViolationException checkFailure =
        new DataIntegrityViolationException(
            "check",
            new ConstraintViolationException(
                "check", new SQLException("check failed", "23514"), "ck_payment_amount"));
    when(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class)))
        .thenThrow(checkFailure);

    assertThatThrownBy(() -> invoiceService.confirmTransferPayment(managerUserId, invoiceId))
        .isSameAs(checkFailure);
  }

  @Test
  @DisplayName(
      "getAdminInvoices: uses default sort when unsorted/null, trims search, forwards overdueOnly, and computes overdue dynamically")
  void getAdminInvoices_appliesDefaultSortFiltersAndDynamicOverdue() {
    Invoice debtOverdue =
        Invoice.builder()
            .id(UUID.randomUUID())
            .invoiceCode("INV-202610-OVERDUE")
            .contract(contract)
            .billingPeriod(LocalDate.of(2026, 10, 1))
            .amount(new BigDecimal("1500000"))
            .dueOn(LocalDate.of(2026, 10, 5))
            .status(InvoiceStatus.DEBT)
            .issuedAt(Instant.parse("2026-09-25T00:00:00Z"))
            .build();
    Invoice debtDueToday =
        Invoice.builder()
            .id(UUID.randomUUID())
            .invoiceCode("INV-202610-DUETODAY")
            .contract(contract)
            .billingPeriod(LocalDate.of(2026, 10, 1))
            .amount(new BigDecimal("1500000"))
            .dueOn(LocalDate.of(2026, 10, 10))
            .status(InvoiceStatus.DEBT)
            .issuedAt(Instant.parse("2026-09-25T00:00:00Z"))
            .build();
    Invoice pendingPastDue =
        Invoice.builder()
            .id(UUID.randomUUID())
            .invoiceCode("INV-202610-PENDING")
            .contract(contract)
            .billingPeriod(LocalDate.of(2026, 10, 1))
            .amount(new BigDecimal("1500000"))
            .dueOn(LocalDate.of(2026, 10, 1))
            .status(InvoiceStatus.PENDING_CONFIRMATION)
            .proofFileKey("invoices/p/proof/img.png")
            .proofSubmittedAt(FIXED_NOW)
            .issuedAt(Instant.parse("2026-09-25T00:00:00Z"))
            .build();
    Invoice paidPastDue =
        Invoice.builder()
            .id(UUID.randomUUID())
            .invoiceCode("INV-202609-PAID")
            .contract(contract)
            .billingPeriod(LocalDate.of(2026, 9, 1))
            .amount(new BigDecimal("1400000"))
            .dueOn(LocalDate.of(2026, 9, 5))
            .status(InvoiceStatus.PAID)
            .paymentTransaction(
                PaymentTransaction.builder()
                    .id(UUID.randomUUID())
                    .receiptCode("PT-INV-202609-PAID")
                    .amount(new BigDecimal("1400000"))
                    .paymentMethod(PaymentMethod.CASH)
                    .confirmedByStaff(managerStaff)
                    .confirmedAt(FIXED_NOW)
                    .build())
            .issuedAt(Instant.parse("2026-08-25T00:00:00Z"))
            .build();

    LocalDate today = LocalDate.of(2026, 10, 10);
    LocalDate octPeriod = LocalDate.of(2026, 10, 1);

    when(invoiceRepository.findInvoicesWithFilters(
            any(), any(), any(), any(Boolean.class), any(), any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(List.of(debtOverdue, debtDueToday, pendingPastDue, paidPastDue)));

    Page<InvoiceSummaryResponse> defaultPage =
        invoiceService.getAdminInvoices(null, "  P.401  ", null, octPeriod, null);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(invoiceRepository)
        .findInvoicesWithFilters(
            eq("P.401"), eq(null), eq(octPeriod), eq(false), eq(today), pageableCaptor.capture());

    Pageable usedPageable = pageableCaptor.getValue();
    assertThat(usedPageable.getPageNumber()).isZero();
    assertThat(usedPageable.getPageSize()).isEqualTo(20);
    assertThat(usedPageable.getSort())
        .containsExactly(Sort.Order.desc("billingPeriod"), Sort.Order.desc("issuedAt"));

    assertThat(defaultPage.getContent()).hasSize(4);
    assertThat(defaultPage.getContent().get(0).overdue()).isTrue();
    assertThat(defaultPage.getContent().get(0).overdueDays()).isEqualTo(5L);
    assertThat(defaultPage.getContent().get(0).tenantName()).isEqualTo("Nguyen Van Proof");
    assertThat(defaultPage.getContent().get(0).roomNumber()).isEqualTo("P.401");

    assertThat(defaultPage.getContent().get(1).overdue()).isFalse();
    assertThat(defaultPage.getContent().get(1).overdueDays()).isZero();

    assertThat(defaultPage.getContent().get(2).overdue()).isFalse();
    assertThat(defaultPage.getContent().get(2).overdueDays()).isZero();
    assertThat(defaultPage.getContent().get(2).proofAvailable()).isTrue();

    assertThat(defaultPage.getContent().get(3).overdue()).isFalse();
    assertThat(defaultPage.getContent().get(3).overdueDays()).isZero();
    assertThat(defaultPage.getContent().get(3).payment()).isNotNull();
    assertThat(defaultPage.getContent().get(3).payment().receiptCode())
        .isEqualTo("PT-INV-202609-PAID");

    // Custom sort + blank search + overdueOnly=true
    Pageable customSort = PageRequest.of(1, 5, Sort.by(Sort.Order.asc("dueOn")));
    invoiceService.getAdminInvoices(customSort, "   ", InvoiceStatus.DEBT, null, Boolean.TRUE);
    verify(invoiceRepository)
        .findInvoicesWithFilters(
            eq(null), eq(InvoiceStatus.DEBT), eq(null), eq(true), eq(today), eq(customSort));
  }

  @Test
  @DisplayName("getAdminInvoices: rejects non-day-01 billingPeriod with INVALID_BILLING_PERIOD")
  void getAdminInvoices_rejectsNonDayOneBillingPeriod() {
    LocalDate invalidPeriod = LocalDate.of(2026, 10, 15);
    assertThatThrownBy(() -> invoiceService.getAdminInvoices(null, null, null, invalidPeriod, null))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVALID_BILLING_PERIOD);

    verifyNoInteractions(invoiceRepository);
  }

  @Test
  @DisplayName(
      "getAdminInvoiceSummary: aggregates amounts and counts accurately, excludes PENDING_CONFIRMATION from overdue, and validates day-01")
  void getAdminInvoiceSummary_aggregatesBucketsAndValidatesDayOne() {
    LocalDate octPeriod = LocalDate.of(2026, 10, 1);
    List<InvoiceRepository.InvoiceSummaryProjection> projections =
        List.of(
            summaryProjection(
                InvoiceStatus.PAID, LocalDate.of(2026, 10, 1), new BigDecimal("1500000")),
            summaryProjection(
                InvoiceStatus.DEBT, LocalDate.of(2026, 10, 10), new BigDecimal("1200000")),
            summaryProjection(
                InvoiceStatus.DEBT, LocalDate.of(2026, 10, 15), new BigDecimal("1300000")),
            summaryProjection(
                InvoiceStatus.DEBT, LocalDate.of(2026, 10, 5), new BigDecimal("1400000")),
            summaryProjection(
                InvoiceStatus.PENDING_CONFIRMATION,
                LocalDate.of(2026, 10, 2),
                new BigDecimal("1600000")));

    when(invoiceRepository.findAllForSummary(octPeriod)).thenReturn(projections);

    InvoiceSummaryStatsResponse stats = invoiceService.getAdminInvoiceSummary(octPeriod);
    assertThat(stats.billingPeriod()).isEqualTo(octPeriod);
    assertThat(stats.paidAmount()).isEqualByComparingTo("1500000");
    assertThat(stats.paidCount()).isEqualTo(1L);
    assertThat(stats.unpaidInTermAmount()).isEqualByComparingTo("2500000");
    assertThat(stats.unpaidInTermCount()).isEqualTo(2L);
    assertThat(stats.overdueAmount()).isEqualByComparingTo("1400000");
    assertThat(stats.overdueCount()).isEqualTo(1L);
    assertThat(stats.pendingConfirmationAmount()).isEqualByComparingTo("1600000");
    assertThat(stats.pendingConfirmationCount()).isEqualTo(1L);
    assertThat(stats.totalExpectedAmount()).isEqualByComparingTo("7000000");
    assertThat(stats.totalInvoiceCount()).isEqualTo(5L);

    when(invoiceRepository.findAllForSummary(null)).thenReturn(List.of());
    InvoiceSummaryStatsResponse emptyStats = invoiceService.getAdminInvoiceSummary(null);
    assertThat(emptyStats.billingPeriod()).isNull();
    assertThat(emptyStats.paidAmount()).isEqualByComparingTo("0");
    assertThat(emptyStats.unpaidInTermAmount()).isEqualByComparingTo("0");
    assertThat(emptyStats.overdueAmount()).isEqualByComparingTo("0");
    assertThat(emptyStats.pendingConfirmationAmount()).isEqualByComparingTo("0");
    assertThat(emptyStats.totalExpectedAmount()).isEqualByComparingTo("0");
    assertThat(emptyStats.totalInvoiceCount()).isZero();

    assertThatThrownBy(() -> invoiceService.getAdminInvoiceSummary(LocalDate.of(2026, 10, 2)))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVALID_BILLING_PERIOD);
  }

  @Test
  @DisplayName("getAdminInvoiceById: returns detail with admin URL or 404 when missing/null")
  void getAdminInvoiceById_returnsDetailOr404() {
    invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    invoice.setProofFileKey("invoices/" + invoiceId + "/proof/doc.png");
    invoice.setProofSubmittedAt(FIXED_NOW);
    when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

    InvoiceDetailResponse detail = invoiceService.getAdminInvoiceById(invoiceId);
    assertThat(detail.id()).isEqualTo(invoiceId);
    assertThat(detail.proof()).isNotNull();
    assertThat(detail.proof().contentUrl())
        .isEqualTo("/api/v1/admin/invoices/" + invoiceId + "/proof/content");
    assertThat(detail.vietQr()).isNull();

    assertThatThrownBy(() -> invoiceService.getAdminInvoiceById(null))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);

    UUID missingId = UUID.randomUUID();
    when(invoiceRepository.findById(missingId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> invoiceService.getAdminInvoiceById(missingId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "getTenantInvoices: returns invoices for ACTIVE and CHECKED_OUT tenants, supports status filter, and throws 404 for missing tenant")
  void getTenantInvoices_supportsActiveCheckedOutAndStatusFilter() {
    VietQrInfoResponse qrInfo =
        VietQrInfoResponse.builder()
            .bankBin("970405")
            .bankName("Agribank")
            .accountNumber("1234567890")
            .accountName("TEST KTX HUB")
            .amount(new BigDecimal("1500000"))
            .transferContent("KTX INV-202610-HD-2026-0099")
            .qrPayload("000201...")
            .build();
    when(vietQrPayloadService.buildForInvoice(invoice)).thenReturn(Optional.of(qrInfo));
    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(invoiceRepository.findByContractTenantIdOrderByBillingPeriodDescIssuedAtDesc(tenantId))
        .thenReturn(List.of(invoice));
    when(invoiceRepository.findByContractTenantIdAndStatusOrderByBillingPeriodDescIssuedAtDesc(
            tenantId, InvoiceStatus.DEBT))
        .thenReturn(List.of(invoice));

    List<InvoiceDetailResponse> unfiltered = invoiceService.getTenantInvoices(tenantUserId, null);
    assertThat(unfiltered).hasSize(1);
    assertThat(unfiltered.getFirst().vietQr()).isEqualTo(qrInfo);
    assertThat(unfiltered.getFirst().overdue()).isTrue();
    assertThat(unfiltered.getFirst().overdueDays()).isEqualTo(5L);

    tenant.setResidentStatus(ResidentStatus.CHECKED_OUT);
    List<InvoiceDetailResponse> filtered =
        invoiceService.getTenantInvoices(tenantUserId, InvoiceStatus.DEBT);
    assertThat(filtered).hasSize(1);

    UUID unknownUserId = UUID.randomUUID();
    when(tenantRepository.findByUserId(unknownUserId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> invoiceService.getTenantInvoices(unknownUserId, null))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", TenantErrorCode.TENANT_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "getTenantInvoiceById: returns owned detail (ACTIVE & CHECKED_OUT) and enforces 404 on cross-tenant or missing invoice")
  void getTenantInvoiceById_enforcesOwnershipAnd404() {
    invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);
    invoice.setProofFileKey("invoices/" + invoiceId + "/proof/doc.png");
    invoice.setProofSubmittedAt(FIXED_NOW);
    tenant.setResidentStatus(ResidentStatus.CHECKED_OUT);

    when(tenantRepository.findByUserId(tenantUserId)).thenReturn(Optional.of(tenant));
    when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

    InvoiceDetailResponse owned = invoiceService.getTenantInvoiceById(tenantUserId, invoiceId);
    assertThat(owned.id()).isEqualTo(invoiceId);
    assertThat(owned.proof()).isNotNull();
    assertThat(owned.proof().contentUrl())
        .isEqualTo("/api/v1/tenant/invoices/" + invoiceId + "/proof/content");

    UUID otherUserId = UUID.randomUUID();
    when(tenantRepository.findByUserId(otherUserId))
        .thenReturn(Optional.of(Tenant.builder().id(UUID.randomUUID()).build()));
    assertThatThrownBy(() -> invoiceService.getTenantInvoiceById(otherUserId, invoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);

    UUID missingInvoiceId = UUID.randomUUID();
    when(invoiceRepository.findById(missingInvoiceId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> invoiceService.getTenantInvoiceById(tenantUserId, missingInvoiceId))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", InvoiceErrorCode.INVOICE_NOT_FOUND);
  }

  private static InvoiceRepository.InvoiceSummaryProjection summaryProjection(
      InvoiceStatus status, LocalDate dueOn, BigDecimal amount) {
    return new InvoiceRepository.InvoiceSummaryProjection() {
      @Override
      public InvoiceStatus getStatus() {
        return status;
      }

      @Override
      public LocalDate getDueOn() {
        return dueOn;
      }

      @Override
      public BigDecimal getAmount() {
        return amount;
      }
    };
  }

  private static DataIntegrityViolationException uniqueConstraintViolation(String constraintName) {
    return new DataIntegrityViolationException(
        "duplicate",
        new ConstraintViolationException(
            "duplicate", new SQLException("duplicate key value", "23505"), constraintName));
  }
}
