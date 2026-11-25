package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.request.contract.RenewContractRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentResponse;
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
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.repositories.BedRepository;
import com.uit.dormmanagement.repositories.ContractDocumentRepository;
import com.uit.dormmanagement.repositories.ContractRenewalRepository;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.ContractService;
import com.uit.dormmanagement.services.StorageService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
  private final StorageService storageService;

  private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
  private static final Set<String> ALLOWED_EXTENSIONS =
      Set.of("pdf", "doc", "docx", "jpg", "jpeg", "png", "webp");

  private static final List<ContractStatus> ACTIVE_STATUSES =
      List.of(ContractStatus.ACTIVE);

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
    return toDetailResponse(contract);
  }

  @Override
  @Transactional
  public ContractDetailResponse renewContract(
      UUID id, RenewContractRequest request, List<MultipartFile> annexDocuments) {
    Contract contract =
        contractRepository
            .findById(id)
            .orElseThrow(() -> new BaseAppException(ContractErrorCode.CONTRACT_NOT_FOUND));

    if (contract.getStatus() == ContractStatus.LIQUIDATED) {
      throw new BaseAppException(ContractErrorCode.CONTRACT_ALREADY_TERMINATED);
    }

    if (request.newEndDate() == null || !request.newEndDate().isAfter(contract.getEndDate())) {
      throw new BaseAppException(ContractErrorCode.INVALID_CONTRACT_DATES);
    }

    LocalDate previousEndDate = contract.getEndDate();
    LocalDate effectiveFrom = previousEndDate.plusDays(1);
    int sequenceNo = (contract.getRenewals() != null ? contract.getRenewals().size() : 0) + 1;

    ContractRenewal renewal =
        ContractRenewal.builder()
            .contract(contract)
            .sequenceNo(sequenceNo)
            .signedOn(LocalDate.now())
            .previousEndOn(previousEndDate)
            .effectiveFrom(effectiveFrom)
            .newEndOn(request.newEndDate())
            .monthlyRent(contract.getMonthlyRent())
            .build();

    if (contract.getRenewals() == null) {
      contract.setRenewals(new ArrayList<>());
    }
    contract.setStatus(ContractStatus.ACTIVE);
    renewal = contractRenewalRepository.save(renewal);
    contract.getRenewals().add(renewal);

    if (annexDocuments != null && !annexDocuments.isEmpty()) {
      for (MultipartFile file : annexDocuments) {
        if (file != null && !file.isEmpty()) {
          validateFile(file);
          String fileUrl = storageService.uploadFile(file, "contracts");
          ContractDocument doc =
              ContractDocument.builder()
                  .contract(contract)
                  .renewal(renewal)
                  .originalFilename(
                      file.getOriginalFilename() != null
                          ? file.getOriginalFilename()
                          : "phu-luc.pdf")
                  .storageFileKey(fileUrl)
                  .mimeType(
                      file.getContentType() != null ? file.getContentType() : "application/pdf")
                  .documentType(ContractDocumentCategory.RENEWAL_ANNEX)
                  .build();
          contractDocumentRepository.save(doc);
          contract.getDocuments().add(doc);
        }
      }
    }

    Contract saved = contractRepository.save(contract);
    log.info("Renewed contract {} to new end date {}", saved.getContractCode(), saved.getEndDate());
    return toDetailResponse(saved);
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
    return toDetailResponse(saved);
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

    List<ContractDocumentResponse> results = new ArrayList<>();
    for (MultipartFile file : files) {
      if (file != null && !file.isEmpty()) {
        validateFile(file);
        String fileUrl = storageService.uploadFile(file, "contracts");
        ContractDocument doc =
            ContractDocument.builder()
                .contract(contract)
                .originalFilename(
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "document")
                .storageFileKey(fileUrl)
                .mimeType(
                    file.getContentType() != null
                        ? file.getContentType()
                        : "application/octet-stream")
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
                .fileUrl(saved.getFileUrl())
                .fileType(saved.getMimeType())
                .fileSize(file.getSize())
                .documentCategory(saved.getDocumentCategory())
                .uploadedAt(saved.getUploadedAt())
                .build());
      }
    }

    log.info("Uploaded {} document(s) for contract {}", results.size(), contract.getContractCode());
    return results;
  }

  @Override
  @Transactional
  public void deleteDocument(UUID contractId, UUID documentId) {
    contractRepository
        .findById(contractId)
        .orElseThrow(() -> new BaseAppException(ContractErrorCode.CONTRACT_NOT_FOUND));

    ContractDocument document =
        contractDocumentRepository
            .findById(documentId)
            .orElseThrow(() -> new BaseAppException(ContractErrorCode.DOCUMENT_NOT_FOUND));

    if (!document.getContract().getId().equals(contractId)) {
      throw new BaseAppException(ContractErrorCode.DOCUMENT_NOT_FOUND);
    }

    storageService.deleteFile(document.getFileUrl());
    contractDocumentRepository.delete(document);
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
        .map(this::toDetailResponse)
        .orElse(null);
  }

  private void validateFile(MultipartFile file) {
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new BaseAppException(ContractErrorCode.DOCUMENT_FILE_SIZE_EXCEEDED);
    }

    String filename = file.getOriginalFilename();
    if (filename == null || !filename.contains(".")) {
      throw new BaseAppException(ContractErrorCode.UNSUPPORTED_DOCUMENT_FORMAT);
    }

    String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new BaseAppException(ContractErrorCode.UNSUPPORTED_DOCUMENT_FORMAT);
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

  private ContractDetailResponse toDetailResponse(Contract contract) {
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
                            .fileUrl(doc.getFileUrl())
                            .fileType(doc.getMimeType())
                            .documentCategory(doc.getDocumentCategory())
                            .uploadedAt(doc.getUploadedAt())
                            .build())
                .toList()
            : List.of();

    return ContractDetailResponse.builder()
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
        .contractDocumentUrl(null)
        .documents(docResponses)
        .signedAt(contract.getSignedAt())
        .createdAt(contract.getCreatedAt())
        .build();
  }
}
