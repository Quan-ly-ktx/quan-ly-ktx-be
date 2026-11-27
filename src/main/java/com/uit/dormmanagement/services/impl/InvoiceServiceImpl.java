package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceDetailResponse;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceProofContent;
import com.uit.dormmanagement.dtos.response.invoice.InvoiceProofResponse;
import com.uit.dormmanagement.dtos.response.invoice.PaymentTransactionResponse;
import com.uit.dormmanagement.dtos.response.invoice.VietQrInfoResponse;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.PaymentTransaction;
import com.uit.dormmanagement.entities.StaffProfile;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.exceptions.error.InvoiceErrorCode;
import com.uit.dormmanagement.exceptions.error.SystemErrorCode;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.PaymentTransactionRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.services.InvoiceService;
import com.uit.dormmanagement.services.PrivateObjectStorage;
import com.uit.dormmanagement.services.StoredObjectContent;
import com.uit.dormmanagement.services.VietQrPayloadService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

  private static final long MAX_PROOF_FILE_SIZE = 5L * 1024 * 1024;
  private static final Map<String, String> EXTENSION_TO_MIME =
      Map.of(
          "jpg", MediaType.IMAGE_JPEG_VALUE,
          "jpeg", MediaType.IMAGE_JPEG_VALUE,
          "png", MediaType.IMAGE_PNG_VALUE,
          "webp", "image/webp");
  private static final Map<String, Set<String>> EXTENSION_TO_ALLOWED_DECLARED_MIMES =
      Map.of(
          "jpg", Set.of("image/jpeg", "image/pjpeg"),
          "jpeg", Set.of("image/jpeg", "image/pjpeg"),
          "png", Set.of("image/png"),
          "webp", Set.of("image/webp"));

  private final InvoiceRepository invoiceRepository;
  private final PaymentTransactionRepository paymentTransactionRepository;
  private final TenantRepository tenantRepository;
  private final PrivateObjectStorage privateObjectStorage;
  private final VietQrPayloadService vietQrPayloadService;
  private final Clock clock;

  public static String adminProofContentPath(UUID invoiceId) {
    return "/api/v1/admin/invoices/" + invoiceId + "/proof/content";
  }

  public static String tenantProofContentPath(UUID invoiceId) {
    return "/api/v1/tenant/invoices/" + invoiceId + "/proof/content";
  }

  @Override
  @Transactional
  public InvoiceDetailResponse submitProof(UUID tenantUserId, UUID invoiceId, MultipartFile file) {
    ValidatedProofFile validated = validateProofFile(file);

    if (tenantUserId == null || invoiceId == null) {
      throw new BaseAppException(InvoiceErrorCode.INVOICE_NOT_FOUND);
    }

    Tenant tenant =
        tenantRepository
            .findByUserId(tenantUserId)
            .orElseThrow(() -> new BaseAppException(InvoiceErrorCode.INVOICE_NOT_FOUND));

    Invoice invoice =
        invoiceRepository
            .findByIdForUpdate(invoiceId)
            .orElseThrow(() -> new BaseAppException(InvoiceErrorCode.INVOICE_NOT_FOUND));

    if (!isOwnedByTenant(invoice, tenant)) {
      throw new BaseAppException(InvoiceErrorCode.INVOICE_NOT_FOUND);
    }

    if (invoice.getStatus() == InvoiceStatus.PAID
        || paymentTransactionRepository.findByInvoiceId(invoiceId).isPresent()) {
      throw new BaseAppException(InvoiceErrorCode.INVOICE_ALREADY_PAID);
    }
    if (invoice.getStatus() != InvoiceStatus.DEBT) {
      throw new BaseAppException(InvoiceErrorCode.INVOICE_NOT_DEBT);
    }

    String objectKey =
        "invoices/" + invoiceId + "/proof/" + UUID.randomUUID() + "." + validated.extension();
    List<String> uploadedKeys = new ArrayList<>(1);
    registerRollbackCleanup(uploadedKeys);

    try {
      try (InputStream is = file.getInputStream()) {
        privateObjectStorage.putObject(
            objectKey, validated.canonicalMimeType(), is, file.getSize());
      }
      uploadedKeys.add(objectKey);

      String previousProofKey = invoice.getProofFileKey();
      invoice.setProofFileKey(objectKey);
      invoice.setProofSubmittedAt(Instant.now(clock));
      invoice.setStatus(InvoiceStatus.PENDING_CONFIRMATION);

      Invoice saved = invoiceRepository.saveAndFlush(invoice);
      if (previousProofKey != null
          && !previousProofKey.isBlank()
          && !previousProofKey.equals(objectKey)) {
        schedulePostCommitDelete(previousProofKey);
      }

      log.info(
          "Submitted payment proof for invoice {} -> {}",
          saved.getInvoiceCode(),
          saved.getStatus());
      return toDetailResponse(saved, false);
    } catch (RuntimeException | IOException e) {
      cleanupUploadedKeys(uploadedKeys);
      if (e instanceof BaseAppException bae) {
        throw bae;
      }
      if (e instanceof RuntimeException re) {
        throw re;
      }
      throw new BaseAppException(
          SystemErrorCode.INTERNAL_SERVER_ERROR, "Không thể đọc dữ liệu ảnh minh chứng");
    }
  }

  @Override
  @Transactional
  public InvoiceDetailResponse rejectProof(UUID invoiceId) {
    if (invoiceId == null) {
      throw new BaseAppException(InvoiceErrorCode.INVOICE_NOT_FOUND);
    }

    Invoice invoice =
        invoiceRepository
            .findByIdForUpdate(invoiceId)
            .orElseThrow(() -> new BaseAppException(InvoiceErrorCode.INVOICE_NOT_FOUND));

    if (invoice.getStatus() == InvoiceStatus.PAID
        || paymentTransactionRepository.findByInvoiceId(invoiceId).isPresent()) {
      throw new BaseAppException(InvoiceErrorCode.INVOICE_ALREADY_PAID);
    }
    if (invoice.getStatus() != InvoiceStatus.PENDING_CONFIRMATION) {
      throw new BaseAppException(InvoiceErrorCode.INVOICE_NOT_PENDING_CONFIRMATION);
    }

    String oldProofKey = invoice.getProofFileKey();
    invoice.setStatus(InvoiceStatus.DEBT);
    invoice.setProofFileKey(null);
    invoice.setProofSubmittedAt(null);

    Invoice saved = invoiceRepository.saveAndFlush(invoice);
    schedulePostCommitDelete(oldProofKey);

    log.info(
        "Rejected payment proof for invoice {} -> {}", saved.getInvoiceCode(), saved.getStatus());
    return toDetailResponse(saved, true);
  }

  @Override
  @Transactional(readOnly = true)
  public InvoiceProofContent loadProofContentForAdmin(UUID invoiceId) {
    if (invoiceId == null) {
      throw new BaseAppException(InvoiceErrorCode.INVOICE_NOT_FOUND);
    }
    Invoice invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new BaseAppException(InvoiceErrorCode.INVOICE_NOT_FOUND));
    return loadStoredProof(invoice);
  }

  @Override
  @Transactional(readOnly = true)
  public InvoiceProofContent loadProofContentForTenant(UUID tenantUserId, UUID invoiceId) {
    if (tenantUserId == null || invoiceId == null) {
      throw new BaseAppException(InvoiceErrorCode.INVOICE_NOT_FOUND);
    }

    Tenant tenant =
        tenantRepository
            .findByUserId(tenantUserId)
            .orElseThrow(() -> new BaseAppException(InvoiceErrorCode.INVOICE_NOT_FOUND));

    Invoice invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new BaseAppException(InvoiceErrorCode.INVOICE_NOT_FOUND));

    if (!isOwnedByTenant(invoice, tenant)) {
      throw new BaseAppException(InvoiceErrorCode.INVOICE_NOT_FOUND);
    }

    return loadStoredProof(invoice);
  }

  private InvoiceProofContent loadStoredProof(Invoice invoice) {
    String proofKey = invoice.getProofFileKey();
    if (proofKey == null || proofKey.isBlank()) {
      throw new BaseAppException(InvoiceErrorCode.INVOICE_PROOF_NOT_FOUND);
    }

    StoredObjectContent stored;
    try {
      stored = privateObjectStorage.getObject(proofKey);
    } catch (ObjectStorageNotFoundException | IllegalArgumentException e) {
      throw new BaseAppException(InvoiceErrorCode.INVOICE_PROOF_NOT_FOUND);
    }

    String contentType = resolveSafeProofContentType(stored.contentType(), proofKey);
    String extension = resolveSafeProofExtension(proofKey, contentType);
    String codePrefix =
        (invoice.getInvoiceCode() != null && !invoice.getInvoiceCode().isBlank())
            ? invoice.getInvoiceCode().trim()
            : "invoice";
    String downloadFilename = codePrefix + "-proof." + extension;

    return new InvoiceProofContent(
        stored.resource(), contentType, stored.contentLength(), downloadFilename);
  }

  private static boolean isOwnedByTenant(Invoice invoice, Tenant tenant) {
    Contract contract = invoice.getContract();
    Tenant owner = contract != null ? contract.getTenant() : null;
    return owner != null && tenant.getId() != null && tenant.getId().equals(owner.getId());
  }

  private record ValidatedProofFile(String extension, String canonicalMimeType) {}

  private ValidatedProofFile validateProofFile(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() <= 0) {
      throw new BaseAppException(InvoiceErrorCode.PROOF_FILE_REQUIRED);
    }
    if (file.getSize() > MAX_PROOF_FILE_SIZE) {
      throw new BaseAppException(InvoiceErrorCode.PROOF_FILE_SIZE_EXCEEDED);
    }

    String filename = file.getOriginalFilename();
    if (filename == null || filename.isBlank()) {
      throw new BaseAppException(InvoiceErrorCode.UNSUPPORTED_PROOF_FORMAT);
    }

    String normalizedSlashes = filename.replace('\\', '/');
    int lastSlash = normalizedSlashes.lastIndexOf('/');
    String nameOnly =
        lastSlash >= 0 ? normalizedSlashes.substring(lastSlash + 1) : normalizedSlashes;
    int dotIndex = nameOnly.lastIndexOf('.');
    if (dotIndex <= 0
        || dotIndex == nameOnly.length() - 1
        || nameOnly.substring(0, dotIndex).isBlank()) {
      throw new BaseAppException(InvoiceErrorCode.UNSUPPORTED_PROOF_FORMAT);
    }

    String extension = nameOnly.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    String canonicalMime = EXTENSION_TO_MIME.get(extension);
    if (canonicalMime == null) {
      throw new BaseAppException(InvoiceErrorCode.UNSUPPORTED_PROOF_FORMAT);
    }

    String rawContentType = file.getContentType();
    if (rawContentType != null && !rawContentType.isBlank()) {
      int semicolonIndex = rawContentType.indexOf(';');
      String baseContentType =
          (semicolonIndex >= 0 ? rawContentType.substring(0, semicolonIndex) : rawContentType)
              .trim()
              .toLowerCase(Locale.ROOT);
      if (!baseContentType.isEmpty()
          && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(baseContentType)) {
        Set<String> allowedDeclared = EXTENSION_TO_ALLOWED_DECLARED_MIMES.get(extension);
        if (allowedDeclared == null || !allowedDeclared.contains(baseContentType)) {
          throw new BaseAppException(InvoiceErrorCode.UNSUPPORTED_PROOF_FORMAT);
        }
      }
    }

    byte[] header = new byte[12];
    int bytesRead;
    try (InputStream is = file.getInputStream()) {
      bytesRead = is.readNBytes(header, 0, header.length);
    } catch (IOException e) {
      throw new BaseAppException(
          SystemErrorCode.INTERNAL_SERVER_ERROR, "Không thể đọc dữ liệu ảnh minh chứng");
    }
    if (!matchesMagicBytes(extension, header, bytesRead)) {
      throw new BaseAppException(InvoiceErrorCode.UNSUPPORTED_PROOF_FORMAT);
    }

    return new ValidatedProofFile(extension, canonicalMime);
  }

  private static boolean matchesMagicBytes(String extension, byte[] header, int bytesRead) {
    return switch (extension) {
      case "jpg", "jpeg" ->
          bytesRead >= 3
              && (header[0] & 0xFF) == 0xFF
              && (header[1] & 0xFF) == 0xD8
              && (header[2] & 0xFF) == 0xFF;
      case "png" ->
          bytesRead >= 8
              && (header[0] & 0xFF) == 0x89
              && header[1] == 0x50
              && header[2] == 0x4E
              && header[3] == 0x47
              && header[4] == 0x0D
              && header[5] == 0x0A
              && header[6] == 0x1A
              && header[7] == 0x0A;
      case "webp" ->
          bytesRead >= 12
              && header[0] == 'R'
              && header[1] == 'I'
              && header[2] == 'F'
              && header[3] == 'F'
              && header[8] == 'W'
              && header[9] == 'E'
              && header[10] == 'B'
              && header[11] == 'P';
      default -> false;
    };
  }

  private static String resolveSafeProofContentType(String storedContentType, String proofKey) {
    if (storedContentType != null && !storedContentType.isBlank()) {
      int semicolonIndex = storedContentType.indexOf(';');
      String base =
          (semicolonIndex >= 0 ? storedContentType.substring(0, semicolonIndex) : storedContentType)
              .trim()
              .toLowerCase(Locale.ROOT);
      if ("image/jpeg".equals(base) || "image/pjpeg".equals(base)) {
        return MediaType.IMAGE_JPEG_VALUE;
      }
      if ("image/png".equals(base)) {
        return MediaType.IMAGE_PNG_VALUE;
      }
      if ("image/webp".equals(base)) {
        return "image/webp";
      }
    }
    String lowerKey = proofKey.toLowerCase(Locale.ROOT);
    if (lowerKey.endsWith(".png")) {
      return MediaType.IMAGE_PNG_VALUE;
    }
    if (lowerKey.endsWith(".webp")) {
      return "image/webp";
    }
    return MediaType.IMAGE_JPEG_VALUE;
  }

  private static String resolveSafeProofExtension(String proofKey, String contentType) {
    String lowerKey = proofKey.toLowerCase(Locale.ROOT);
    if (lowerKey.endsWith(".png")) {
      return "png";
    }
    if (lowerKey.endsWith(".webp")) {
      return "webp";
    }
    if (lowerKey.endsWith(".jpg") || lowerKey.endsWith(".jpeg")) {
      return "jpg";
    }
    if (MediaType.IMAGE_PNG_VALUE.equals(contentType)) {
      return "png";
    }
    if ("image/webp".equals(contentType)) {
      return "webp";
    }
    return "jpg";
  }

  private void registerRollbackCleanup(List<String> uploadedKeys) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              cleanupUploadedKeys(uploadedKeys);
            }
          }
        });
  }

  private void cleanupUploadedKeys(List<String> uploadedKeys) {
    for (String key : uploadedKeys) {
      deleteObjectBestEffort(key);
    }
    uploadedKeys.clear();
  }

  private void schedulePostCommitDelete(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      return;
    }
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              deleteObjectBestEffort(storageKey);
            }
          });
    } else {
      deleteObjectBestEffort(storageKey);
    }
  }

  private void deleteObjectBestEffort(String storageKey) {
    try {
      privateObjectStorage.deleteObject(storageKey);
    } catch (Exception e) {
      log.warn("Best-effort proof object deletion failed: {}", e.getClass().getSimpleName());
    }
  }

  private InvoiceDetailResponse toDetailResponse(Invoice invoice, boolean adminView) {
    Contract contract = invoice.getContract();
    Tenant tenant = contract != null ? contract.getTenant() : null;
    User user = tenant != null ? tenant.getUser() : null;

    LocalDate today = LocalDate.now(clock.withZone(TimeConfig.DORM_ZONE));
    boolean overdue =
        invoice.getStatus() == InvoiceStatus.DEBT
            && invoice.getDueOn() != null
            && invoice.getDueOn().isBefore(today);
    long overdueDays = overdue ? ChronoUnit.DAYS.between(invoice.getDueOn(), today) : 0L;

    boolean proofAvailable =
        invoice.getProofFileKey() != null && !invoice.getProofFileKey().isBlank();
    InvoiceProofResponse proof =
        proofAvailable
            ? InvoiceProofResponse.builder()
                .available(true)
                .submittedAt(invoice.getProofSubmittedAt())
                .contentUrl(
                    adminView
                        ? adminProofContentPath(invoice.getId())
                        : tenantProofContentPath(invoice.getId()))
                .build()
            : null;

    PaymentTransactionResponse payment =
        invoice.getStatus() == InvoiceStatus.PAID
            ? toPaymentTransactionResponse(invoice.getPaymentTransaction())
            : null;

    VietQrInfoResponse vietQr =
        invoice.getStatus() == InvoiceStatus.DEBT
            ? vietQrPayloadService.buildForInvoice(invoice).orElse(null)
            : null;

    return InvoiceDetailResponse.builder()
        .id(invoice.getId())
        .invoiceCode(invoice.getInvoiceCode())
        .contractId(contract != null ? contract.getId() : null)
        .contractCode(contract != null ? contract.getContractCode() : null)
        .tenantId(tenant != null ? tenant.getId() : null)
        .tenantCode(tenant != null ? tenant.getTenantCode() : null)
        .tenantName(tenant != null ? tenant.getFullName() : null)
        .tenantPhone(tenant != null ? tenant.getPhone() : null)
        .tenantIdCardNumber(tenant != null ? tenant.getIdCardNumber() : null)
        .tenantEmail(user != null ? user.getEmail() : null)
        .roomNumber(contract != null ? contract.getRoomCodeSnapshot() : null)
        .bedCode(contract != null ? contract.getBedCodeSnapshot() : null)
        .billingPeriod(invoice.getBillingPeriod())
        .amount(invoice.getAmount())
        .dueOn(invoice.getDueOn())
        .status(invoice.getStatus())
        .overdue(overdue)
        .overdueDays(overdueDays)
        .proofAvailable(proofAvailable)
        .proofSubmittedAt(invoice.getProofSubmittedAt())
        .proof(proof)
        .payment(payment)
        .vietQr(vietQr)
        .issuedAt(invoice.getIssuedAt())
        .build();
  }

  private static PaymentTransactionResponse toPaymentTransactionResponse(PaymentTransaction tx) {
    if (tx == null) {
      return null;
    }
    StaffProfile staff = tx.getConfirmedByStaff();
    return PaymentTransactionResponse.builder()
        .id(tx.getId())
        .receiptCode(tx.getReceiptCode())
        .amount(tx.getAmount())
        .paymentMethod(tx.getPaymentMethod())
        .confirmedByStaffId(staff != null ? staff.getId() : null)
        .confirmedByStaffName(staff != null ? staff.getFullName() : null)
        .confirmedAt(tx.getConfirmedAt())
        .build();
  }
}
