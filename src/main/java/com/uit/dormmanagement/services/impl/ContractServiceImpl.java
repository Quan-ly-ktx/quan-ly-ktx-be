package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.request.contract.RenewContractRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentContent;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractRenewalResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractSummaryResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.ContractDocument;
import com.uit.dormmanagement.entities.ContractRenewal;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.BedStatus;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.DataIntegrityViolationClassifier;
import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.RoomErrorCode;
import com.uit.dormmanagement.exceptions.error.SystemErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractDocumentRepository;
import com.uit.dormmanagement.repositories.ContractRenewalRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.ContractService;
import com.uit.dormmanagement.services.InvoiceIssuanceService;
import com.uit.dormmanagement.services.PrivateObjectStorage;
import com.uit.dormmanagement.services.StoredObjectContent;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

  private final ContractRepository contractRepository;
  private final ContractRenewalRepository contractRenewalRepository;
  private final ContractDocumentRepository contractDocumentRepository;
  private final BedRepository bedRepository;
  private final TenantRepository tenantRepository;
  private final VehicleRepository vehicleRepository;
  private final PrivateObjectStorage privateObjectStorage;
  private final InvoiceIssuanceService invoiceIssuanceService;
  private final Clock clock;

  private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
  private static final Map<String, String> EXTENSION_TO_MIME =
      Map.of(
          "pdf", "application/pdf",
          "doc", "application/msword",
          "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "jpg", "image/jpeg",
          "jpeg", "image/jpeg",
          "png", "image/png",
          "webp", "image/webp");
  private static final Set<String> ALLOWED_MIME_TYPES = Set.copyOf(EXTENSION_TO_MIME.values());

  private static final List<ContractStatus> ACTIVE_STATUSES = List.of(ContractStatus.ACTIVE);

  public static String adminDocumentContentPath(UUID contractId, UUID documentId) {
    return "/api/v1/admin/contracts/" + contractId + "/documents/" + documentId + "/content";
  }

  public static String tenantDocumentContentPath(UUID contractId, UUID documentId) {
    return "/api/v1/tenant/contracts/" + contractId + "/documents/" + documentId + "/content";
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ContractSummaryResponse> getContracts(
      Pageable pageable, String search, ContractStatus status) {
    String query = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
    Page<Contract> contracts = contractRepository.findContractsWithFilters(query, status, pageable);
    return contracts.map(this::toSummaryResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public ContractDetailResponse getContractById(UUID id) {
    Contract contract =
        contractRepository
            .findById(id)
            .orElseThrow(() -> new BaseAppException(ContractErrorCode.CONTRACT_NOT_FOUND));
    return toDetailResponse(contract, true);
  }

  @Override
  @Transactional
  public ContractDetailResponse renewContract(
      UUID id, RenewContractRequest request, List<MultipartFile> annexDocuments) {
    Contract contract =
        contractRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new BaseAppException(ContractErrorCode.CONTRACT_NOT_FOUND));

    if (contract.getStatus() != ContractStatus.ACTIVE) {
      throw new BaseAppException(ContractErrorCode.CONTRACT_ALREADY_TERMINATED);
    }

    Tenant tenant = contract.getTenant();
    if (tenant == null || tenant.getResidentStatus() != ResidentStatus.ACTIVE) {
      throw new BaseAppException(TenantErrorCode.TENANT_NOT_ACTIVE);
    }

    Bed bed = contract.getBed();
    if (bed == null || bed.getStatus() != BedStatus.OCCUPIED) {
      throw new BaseAppException(ContractErrorCode.BED_NOT_AVAILABLE);
    }

    Room room = bed.getRoom();
    if (room == null) {
      throw new BaseAppException(RoomErrorCode.ROOM_NOT_FOUND);
    }
    if (room.getArchivedAt() != null) {
      throw new BaseAppException(ContractErrorCode.ROOM_ARCHIVED);
    }

    Optional<ContractRenewal> latestRenewalOpt =
        contractRenewalRepository.findFirstByContractIdOrderBySequenceNoDesc(contract.getId());
    LocalDate currentEndDate =
        latestRenewalOpt.map(ContractRenewal::getNewEndOn).orElse(contract.getOriginalEndOn());
    int nextSequenceNo = latestRenewalOpt.map(r -> r.getSequenceNo() + 1).orElse(1);
    if (currentEndDate == null) {
      throw new BaseAppException(ContractErrorCode.INVALID_CONTRACT_DATES);
    }

    LocalDate today = LocalDate.now(clock.withZone(TimeConfig.DORM_ZONE));
    if (today.isAfter(currentEndDate)) {
      throw new BaseAppException(ContractErrorCode.CONTRACT_EXPIRED);
    }

    if (invoiceIssuanceService.hasUnsettledInvoices(tenant.getId())) {
      throw new BaseAppException(ContractErrorCode.TENANT_HAS_UNSETTLED_INVOICES);
    }

    if (request == null || request.newEndDate() == null) {
      throw new BaseAppException(ContractErrorCode.INVALID_CONTRACT_DATES);
    }
    LocalDate newEndDate = request.newEndDate();
    LocalDate effectiveFrom = currentEndDate.plusDays(1);
    if (!newEndDate.isAfter(currentEndDate)
        || !isLastDayOfMonth(newEndDate)
        || effectiveFrom.getDayOfMonth() != 1) {
      throw new BaseAppException(ContractErrorCode.INVALID_CONTRACT_DATES);
    }

    BigDecimal lockedRoomPrice = validateAndNormalizeMonthlyRent(room.getBasePrice());

    ContractRenewal renewal =
        ContractRenewal.builder()
            .contract(contract)
            .sequenceNo(nextSequenceNo)
            .signedOn(today)
            .previousEndOn(currentEndDate)
            .effectiveFrom(effectiveFrom)
            .newEndOn(newEndDate)
            .monthlyRent(lockedRoomPrice)
            .build();

    ContractRenewal savedRenewal;
    try {
      savedRenewal = contractRenewalRepository.saveAndFlush(renewal);
    } catch (DataIntegrityViolationException e) {
      if (DataIntegrityViolationClassifier.matchesUniqueConstraint(
          e, "uq_contract_renewals_sequence", "uq_contract_renewals_effective")) {
        throw new BaseAppException(ContractErrorCode.CONTRACT_RENEWAL_CONFLICT);
      }
      throw e;
    }

    syncRenewalInMemory(contract, savedRenewal);

    invoiceIssuanceService.issueRenewalCatchUpInvoiceIfEligible(contract, savedRenewal);

    if (annexDocuments != null && !annexDocuments.isEmpty()) {
      List<String> uploadedKeys = new ArrayList<>();
      registerRollbackCleanup(uploadedKeys);
      try {
        for (MultipartFile file : annexDocuments) {
          if (file != null && !file.isEmpty()) {
            String extension = validateFileAndExtractExtension(file);
            String originalFilename =
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "phu-luc.pdf";
            String objectKey =
                buildDocumentObjectKey(contract.getId(), originalFilename, extension);
            String mimeType = resolveDocumentMimeType(file, extension);
            try (InputStream is = file.getInputStream()) {
              privateObjectStorage.putObject(objectKey, mimeType, is, file.getSize());
            }
            uploadedKeys.add(objectKey);

            ContractDocument doc =
                ContractDocument.builder()
                    .contract(contract)
                    .renewal(savedRenewal)
                    .originalFilename(originalFilename)
                    .storageFileKey(objectKey)
                    .mimeType(mimeType)
                    .documentType(ContractDocumentCategory.RENEWAL_ANNEX)
                    .build();
            ContractDocument savedDoc = contractDocumentRepository.saveAndFlush(doc);
            syncDocumentInMemory(contract, savedDoc);
          }
        }
      } catch (RuntimeException | IOException e) {
        cleanupUploadedKeys(uploadedKeys);
        if (e instanceof BaseAppException bae) {
          throw bae;
        }
        if (e instanceof RuntimeException re) {
          throw re;
        }
        throw new BaseAppException(
            SystemErrorCode.INTERNAL_SERVER_ERROR, "Không thể đọc dữ liệu tài liệu hợp đồng");
      }
    }

    log.info(
        "Renewed contract {} (seq={}) to new end date {} with locked rent {}",
        contract.getContractCode(),
        savedRenewal.getSequenceNo(),
        savedRenewal.getNewEndOn(),
        savedRenewal.getMonthlyRent());
    return toDetailResponse(contract, true);
  }

  @Override
  @Transactional
  public ContractDetailResponse terminateContract(UUID id) {
    Contract contract =
        contractRepository
            .findById(id)
            .orElseThrow(() -> new BaseAppException(ContractErrorCode.CONTRACT_NOT_FOUND));

    if (contract.getStatus() == ContractStatus.LIQUIDATED) {
      throw new BaseAppException(ContractErrorCode.CONTRACT_ALREADY_TERMINATED);
    }

    // 1. Contract -> LIQUIDATED
    contract.setStatus(ContractStatus.LIQUIDATED);
    contract.setCheckedOutAt(Instant.now());

    // 2. Bed -> AVAILABLE; room occupancy is derived from Bed status.
    Bed bed = contract.getBed();
    if (bed != null) {
      bed.setStatus(BedStatus.AVAILABLE);
      bedRepository.save(bed);
    }

    // 3. Tenant -> CHECKED_OUT
    Tenant tenant = contract.getTenant();
    if (tenant != null) {
      tenant.setResidentStatus(ResidentStatus.CHECKED_OUT);
      tenantRepository.save(tenant);

      // 4. Vehicle -> REVOKED
      List<Vehicle> vehicles = vehicleRepository.findByTenantId(tenant.getId());
      for (Vehicle vehicle : vehicles) {
        if (vehicle.getStatus() == VehicleStatus.ISSUED
            || vehicle.getStatus() == VehicleStatus.PENDING_CARD) {
          vehicle.setStatus(VehicleStatus.REVOKED);
          vehicle.setRevokedAt(Instant.now());
          vehicleRepository.save(vehicle);
          log.info(
              "Revoked vehicle {} on checkout for tenant {}",
              vehicle.getLicensePlate(),
              tenant.getTenantCode());
        }
      }
    }

    Contract saved = contractRepository.save(contract);
    log.info(
        "Checkout completed: Liquidated contract {} and released bed", saved.getContractCode());
    return toDetailResponse(saved, true);
  }

  @Override
  @Transactional
  public List<ContractDocumentResponse> uploadDocuments(
      UUID contractId, ContractDocumentCategory category, List<MultipartFile> files) {
    Contract contract =
        contractRepository
            .findById(contractId)
            .orElseThrow(() -> new BaseAppException(ContractErrorCode.CONTRACT_NOT_FOUND));

    if (files == null || files.isEmpty()) {
      return List.of();
    }

    if (contract.getDocuments() == null) {
      contract.setDocuments(new ArrayList<>());
    }

    List<String> uploadedKeys = new ArrayList<>();
    registerRollbackCleanup(uploadedKeys);

    List<ContractDocumentResponse> results = new ArrayList<>();
    try {
      for (MultipartFile file : files) {
        if (file != null && !file.isEmpty()) {
          String extension = validateFileAndExtractExtension(file);
          String originalFilename =
              file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
          String objectKey = buildDocumentObjectKey(contractId, originalFilename, extension);
          String mimeType = resolveDocumentMimeType(file, extension);
          try (InputStream is = file.getInputStream()) {
            privateObjectStorage.putObject(objectKey, mimeType, is, file.getSize());
          }
          uploadedKeys.add(objectKey);

          ContractDocument doc =
              ContractDocument.builder()
                  .contract(contract)
                  .originalFilename(originalFilename)
                  .storageFileKey(objectKey)
                  .mimeType(mimeType)
                  .documentType(
                      category != null ? category : ContractDocumentCategory.ORIGINAL_CONTRACT)
                  .build();
          ContractDocument saved = contractDocumentRepository.save(doc);
          contract.getDocuments().add(saved);

          results.add(
              ContractDocumentResponse.builder()
                  .id(saved.getId())
                  .contractId(contractId)
                  .fileName(saved.getFileName())
                  .fileUrl(adminDocumentContentPath(contractId, saved.getId()))
                  .fileType(saved.getMimeType())
                  .fileSize(file.getSize())
                  .documentCategory(saved.getDocumentCategory())
                  .uploadedAt(saved.getUploadedAt())
                  .build());
        }
      }
    } catch (RuntimeException | IOException e) {
      cleanupUploadedKeys(uploadedKeys);
      if (e instanceof BaseAppException bae) {
        throw bae;
      }
      if (e instanceof RuntimeException re) {
        throw re;
      }
      throw new BaseAppException(
          SystemErrorCode.INTERNAL_SERVER_ERROR, "Không thể đọc dữ liệu tài liệu hợp đồng");
    }

    log.info("Uploaded {} document(s) for contract {}", results.size(), contract.getContractCode());
    return results;
  }

  @Override
  @Transactional
  public void deleteDocument(UUID contractId, UUID documentId) {
    Contract contract =
        contractRepository
            .findById(contractId)
            .orElseThrow(() -> new BaseAppException(ContractErrorCode.CONTRACT_NOT_FOUND));

    ContractDocument document =
        contractDocumentRepository
            .findById(documentId)
            .orElseThrow(() -> new BaseAppException(ContractErrorCode.DOCUMENT_NOT_FOUND));

    if (document.getContract() == null || !document.getContract().getId().equals(contractId)) {
      throw new BaseAppException(ContractErrorCode.DOCUMENT_NOT_FOUND);
    }

    String storageKey = document.getStorageFileKey();
    if (contract.getDocuments() != null) {
      contract.getDocuments().removeIf(d -> documentId.equals(d.getId()));
    }
    contractDocumentRepository.delete(document);
    contractDocumentRepository.flush();

    schedulePostCommitDelete(storageKey);
    log.info("Deleted contract document {} from contract {}", documentId, contractId);
  }

  @Override
  @Transactional(readOnly = true)
  public ContractDetailResponse getMyContract(UUID userId) {
    Tenant tenant =
        tenantRepository
            .findByUserId(userId)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    return contractRepository
        .findFirstByTenantIdAndStatusIn(tenant.getId(), ACTIVE_STATUSES)
        .map(contract -> toDetailResponse(contract, false))
        .orElse(null);
  }

  @Override
  @Transactional(readOnly = true)
  public ContractDocumentContent loadDocumentContentForAdmin(UUID contractId, UUID documentId) {
    ContractDocument document =
        contractDocumentRepository
            .findById(documentId)
            .orElseThrow(() -> new BaseAppException(ContractErrorCode.DOCUMENT_NOT_FOUND));

    if (document.getContract() == null || !contractId.equals(document.getContract().getId())) {
      throw new BaseAppException(ContractErrorCode.DOCUMENT_NOT_FOUND);
    }

    return loadStoredDocument(document);
  }

  @Override
  @Transactional(readOnly = true)
  public ContractDocumentContent loadDocumentContentForTenant(
      UUID tenantUserId, UUID contractId, UUID documentId) {
    Tenant tenant =
        tenantRepository
            .findByUserId(tenantUserId)
            .orElseThrow(() -> new BaseAppException(ContractErrorCode.CONTRACT_NOT_FOUND));

    Contract contract =
        contractRepository
            .findById(contractId)
            .orElseThrow(() -> new BaseAppException(ContractErrorCode.CONTRACT_NOT_FOUND));

    if (contract.getTenant() == null || !tenant.getId().equals(contract.getTenant().getId())) {
      throw new BaseAppException(ContractErrorCode.CONTRACT_NOT_FOUND);
    }

    ContractDocument document =
        contractDocumentRepository
            .findById(documentId)
            .orElseThrow(() -> new BaseAppException(ContractErrorCode.DOCUMENT_NOT_FOUND));

    if (document.getContract() == null || !contractId.equals(document.getContract().getId())) {
      throw new BaseAppException(ContractErrorCode.DOCUMENT_NOT_FOUND);
    }

    return loadStoredDocument(document);
  }

  private ContractDocumentContent loadStoredDocument(ContractDocument document) {
    String storageKey = document.getStorageFileKey();
    if (storageKey == null || storageKey.isBlank()) {
      throw new BaseAppException(ContractErrorCode.DOCUMENT_NOT_FOUND);
    }

    StoredObjectContent stored;
    try {
      stored = privateObjectStorage.getObject(storageKey);
    } catch (ObjectStorageNotFoundException | IllegalArgumentException e) {
      throw new BaseAppException(ContractErrorCode.DOCUMENT_NOT_FOUND);
    }

    String contentType =
        (document.getMimeType() != null && !document.getMimeType().isBlank())
            ? document.getMimeType()
            : stored.contentType();
    if (contentType == null || contentType.isBlank()) {
      contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    return new ContractDocumentContent(
        stored.resource(), contentType, stored.contentLength(), document.getOriginalFilename());
  }

  private String validateFileAndExtractExtension(MultipartFile file) {
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new BaseAppException(ContractErrorCode.DOCUMENT_FILE_SIZE_EXCEEDED);
    }

    String filename = file.getOriginalFilename();
    if (filename == null || filename.isBlank()) {
      throw new BaseAppException(ContractErrorCode.UNSUPPORTED_DOCUMENT_FORMAT);
    }

    String normalizedSlashes = filename.replace('\\', '/');
    int lastSlash = normalizedSlashes.lastIndexOf('/');
    String nameOnly =
        lastSlash >= 0 ? normalizedSlashes.substring(lastSlash + 1) : normalizedSlashes;
    int dotIndex = nameOnly.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == nameOnly.length() - 1) {
      throw new BaseAppException(ContractErrorCode.UNSUPPORTED_DOCUMENT_FORMAT);
    }

    String extension = nameOnly.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    if (!EXTENSION_TO_MIME.containsKey(extension)) {
      throw new BaseAppException(ContractErrorCode.UNSUPPORTED_DOCUMENT_FORMAT);
    }
    return extension;
  }

  private String buildDocumentObjectKey(
      UUID contractId, String originalFilename, String extension) {
    String normalizedSlashes = originalFilename.replace('\\', '/');
    int lastSlash = normalizedSlashes.lastIndexOf('/');
    String nameOnly =
        lastSlash >= 0 ? normalizedSlashes.substring(lastSlash + 1) : normalizedSlashes;
    int dotIndex = nameOnly.lastIndexOf('.');
    String baseName = dotIndex >= 0 ? nameOnly.substring(0, dotIndex) : nameOnly;

    String cleanBaseName =
        Normalizer.normalize(baseName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .replaceAll("[^a-zA-Z0-9_-]", "_")
            .replaceAll("_+", "_");
    if (cleanBaseName.isBlank()) {
      cleanBaseName = "document";
    }
    if (cleanBaseName.length() > 100) {
      cleanBaseName = cleanBaseName.substring(0, 100);
    }

    return "contracts/"
        + contractId
        + "/"
        + UUID.randomUUID()
        + "_"
        + cleanBaseName
        + "."
        + extension;
  }

  private String resolveDocumentMimeType(MultipartFile file, String extension) {
    String rawContentType = file.getContentType();
    if (rawContentType != null) {
      String normalized = rawContentType.trim().toLowerCase(Locale.ROOT);
      if (ALLOWED_MIME_TYPES.contains(normalized)) {
        return normalized;
      }
    }
    return EXTENSION_TO_MIME.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM_VALUE);
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
      log.warn("Best-effort object deletion failed: {}", e.getClass().getSimpleName());
    }
  }

  private ContractSummaryResponse toSummaryResponse(Contract contract) {
    Tenant tenant = contract.getTenant();
    Bed bed = contract.getBed();
    Room room = bed != null ? bed.getRoom() : null;

    return ContractSummaryResponse.builder()
        .id(contract.getId())
        .contractCode(contract.getContractCode())
        .tenantId(tenant != null ? tenant.getId() : null)
        .tenantName(tenant != null ? tenant.getFullName() : null)
        .tenantPhone(tenant != null ? tenant.getPhone() : null)
        .roomNumber(room != null ? room.getRoomNumber() : null)
        .bedCode(bed != null ? bed.getBedCode() : null)
        .startDate(contract.getStartDate())
        .endDate(contract.getEndDate())
        .monthlyRent(contract.getMonthlyRent())
        .depositAmount(contract.getDepositAmount())
        .status(contract.getStatus())
        .build();
  }

  private static boolean isLastDayOfMonth(LocalDate date) {
    return date.equals(date.withDayOfMonth(date.lengthOfMonth()));
  }

  private static BigDecimal validateAndNormalizeMonthlyRent(BigDecimal basePrice) {
    if (basePrice == null
        || basePrice.compareTo(BigDecimal.ZERO) <= 0
        || basePrice.stripTrailingZeros().scale() > 0) {
      throw new IllegalStateException("Room basePrice must be a positive integer VND value");
    }
    BigDecimal normalized = basePrice.setScale(0, RoundingMode.UNNECESSARY);
    if (normalized.precision() > 14) {
      throw new IllegalStateException("Room basePrice exceeds numeric(14,0)");
    }
    return normalized;
  }

  private static void syncRenewalInMemory(Contract contract, ContractRenewal savedRenewal) {
    if (contract.getRenewals() == null) {
      contract.setRenewals(new ArrayList<>());
    }
    boolean alreadyPresent =
        contract.getRenewals().stream()
            .anyMatch(
                existing ->
                    existing == savedRenewal
                        || (savedRenewal.getId() != null
                            && savedRenewal.getId().equals(existing.getId()))
                        || (savedRenewal.getSequenceNo() != null
                            && savedRenewal.getSequenceNo().equals(existing.getSequenceNo())));
    if (!alreadyPresent) {
      contract.getRenewals().add(savedRenewal);
    }
  }

  private static void syncDocumentInMemory(Contract contract, ContractDocument savedDoc) {
    if (contract.getDocuments() == null) {
      contract.setDocuments(new ArrayList<>());
    }
    boolean alreadyPresent =
        contract.getDocuments().stream()
            .anyMatch(
                existing ->
                    existing == savedDoc
                        || (savedDoc.getId() != null && savedDoc.getId().equals(existing.getId())));
    if (!alreadyPresent) {
      contract.getDocuments().add(savedDoc);
    }
  }

  private ContractDetailResponse toDetailResponse(Contract contract, boolean adminView) {
    Tenant tenant = contract.getTenant();
    Bed bed = contract.getBed();
    Room room = bed != null ? bed.getRoom() : null;

    List<ContractDocumentResponse> docResponses =
        contract.getDocuments() != null
            ? contract.getDocuments().stream()
                .map(
                    doc ->
                        ContractDocumentResponse.builder()
                            .id(doc.getId())
                            .contractId(contract.getId())
                            .fileName(doc.getFileName())
                            .fileUrl(
                                adminView
                                    ? adminDocumentContentPath(contract.getId(), doc.getId())
                                    : tenantDocumentContentPath(contract.getId(), doc.getId()))
                            .fileType(doc.getMimeType())
                            .fileSize(doc.getFileSize())
                            .documentCategory(doc.getDocumentCategory())
                            .uploadedAt(doc.getUploadedAt())
                            .build())
                .toList()
            : List.of();

    List<ContractRenewalResponse> renewalResponses =
        contract.getRenewals() != null
            ? contract.getRenewals().stream()
                .sorted(
                    Comparator.comparing(
                        ContractRenewal::getSequenceNo,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(
                    renewal ->
                        ContractRenewalResponse.builder()
                            .id(renewal.getId())
                            .sequenceNo(renewal.getSequenceNo())
                            .signedOn(renewal.getSignedOn())
                            .previousEndOn(renewal.getPreviousEndOn())
                            .effectiveFrom(renewal.getEffectiveFrom())
                            .newEndOn(renewal.getNewEndOn())
                            .monthlyRent(renewal.getMonthlyRent())
                            .createdAt(renewal.getCreatedAt())
                            .build())
                .toList()
            : List.of();

    LocalDate effectiveEndDate =
        !renewalResponses.isEmpty() ? renewalResponses.getLast().newEndOn() : contract.getEndDate();
    BigDecimal effectiveMonthlyRent =
        !renewalResponses.isEmpty()
            ? renewalResponses.getLast().monthlyRent()
            : contract.getMonthlyRent();

    return ContractDetailResponse.builder()
        .id(contract.getId())
        .contractCode(contract.getContractCode())
        .tenantId(tenant != null ? tenant.getId() : null)
        .tenantName(tenant != null ? tenant.getFullName() : null)
        .tenantPhone(tenant != null ? tenant.getPhone() : null)
        .bedId(bed != null ? bed.getId() : null)
        .bedCode(bed != null ? bed.getBedCode() : null)
        .roomId(room != null ? room.getId() : null)
        .roomNumber(room != null ? room.getRoomNumber() : null)
        .startDate(contract.getStartDate())
        .endDate(effectiveEndDate)
        .monthlyRent(effectiveMonthlyRent)
        .depositAmount(contract.getDepositAmount())
        .status(contract.getStatus())
        .contractDocumentUrl(null)
        .documents(docResponses)
        .renewals(renewalResponses)
        .signedAt(contract.getSignedAt())
        .createdAt(contract.getCreatedAt())
        .build();
  }
}
