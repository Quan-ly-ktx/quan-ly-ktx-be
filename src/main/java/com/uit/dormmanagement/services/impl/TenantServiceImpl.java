package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.request.tenant.UpdateTenantRequest;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractSummaryResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantDetailResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantSummaryResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.entities.Bed;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.entities.Vehicle;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.repositories.ContractRepository;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import com.uit.dormmanagement.repositories.VehicleRepository;
import com.uit.dormmanagement.services.TenantService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final ContractRepository contractRepository;
  private final VehicleRepository vehicleRepository;

  private static final List<ContractStatus> ACTIVE_CONTRACT_STATUSES =
      List.of(ContractStatus.ACTIVE, ContractStatus.EXPIRING_SOON);

  @Override
  @Transactional(readOnly = true)
  public Page<TenantSummaryResponse> getTenants(
      Pageable pageable, String search, UUID roomId, ResidentStatus status) {
    String query = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
    Page<Tenant> tenantsPage =
        tenantRepository.findActiveTenantsWithFilters(query, roomId, status, pageable);

    return tenantsPage.map(this::toSummaryResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public TenantDetailResponse getTenantById(UUID id) {
    Tenant tenant =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    return toDetailResponse(tenant);
  }

  @Override
  @Transactional
  public TenantDetailResponse updateTenant(UUID id, UpdateTenantRequest request) {
    Tenant tenant =
        tenantRepository
            .findById(id)
            .orElseThrow(() -> new BaseAppException(TenantErrorCode.TENANT_NOT_FOUND));

    String cleanPhone = request.phone().trim();
    if (!cleanPhone.equals(tenant.getPhone())) {
      boolean phoneExistsInTenants =
          tenantRepository.findByPhone(cleanPhone).filter(t -> !t.getId().equals(id)).isPresent();

      boolean phoneExistsInUsers =
          userRepository
              .findByPhone(cleanPhone)
              .filter(u -> tenant.getUser() == null || !u.getId().equals(tenant.getUser().getId()))
              .isPresent();

      if (phoneExistsInTenants || phoneExistsInUsers) {
        throw new BaseAppException(TenantErrorCode.PHONE_DUPLICATED);
      }
    }

    tenant.setFullName(request.fullName().trim());
    tenant.setPhone(cleanPhone);
    tenant.setPermanentAddress(
        request.permanentAddress() != null ? request.permanentAddress().trim() : null);
    tenant.setDateOfBirth(request.dateOfBirth());
    tenant.setEmergencyContactName(
        request.emergencyContactName() != null ? request.emergencyContactName().trim() : null);
    tenant.setEmergencyContactPhone(
        request.emergencyContactPhone() != null ? request.emergencyContactPhone().trim() : null);

    User user = tenant.getUser();
    if (user != null && !cleanPhone.equals(user.getPhone())) {
      user.setPhone(cleanPhone);
      userRepository.save(user);
    }

    Tenant savedTenant = tenantRepository.save(tenant);
    log.info(
        "Updated profile for tenant {} ({})", savedTenant.getTenantCode(), savedTenant.getId());
    return toDetailResponse(savedTenant);
  }

  private TenantSummaryResponse toSummaryResponse(Tenant tenant) {
    Optional<Contract> activeContractOpt =
        contractRepository.findFirstByTenantIdAndStatusInAndDeletedAtIsNull(
            tenant.getId(), ACTIVE_CONTRACT_STATUSES);

    String roomNumber = null;
    String bedCode = null;
    java.time.LocalDate contractEndDate = null;

    if (activeContractOpt.isPresent()) {
      Contract contract = activeContractOpt.get();
      contractEndDate = contract.getEndDate();
      Bed bed = contract.getBed();
      if (bed != null) {
        bedCode = bed.getBedCode();
        Room room = bed.getRoom();
        if (room != null) {
          roomNumber = room.getRoomNumber();
        }
      }
    }

    return TenantSummaryResponse.builder()
        .id(tenant.getId())
        .tenantCode(tenant.getTenantCode())
        .fullName(tenant.getFullName())
        .gender(tenant.getGender())
        .idCardNumber(tenant.getIdCardNumber())
        .phone(tenant.getPhone())
        .email(tenant.getEmail())
        .residentStatus(tenant.getResidentStatus())
        .roomNumber(roomNumber)
        .bedCode(bedCode)
        .contractEndDate(contractEndDate)
        .build();
  }

  private TenantDetailResponse toDetailResponse(Tenant tenant) {
    Optional<Contract> activeContractOpt =
        contractRepository.findFirstByTenantIdAndStatusInAndDeletedAtIsNull(
            tenant.getId(), ACTIVE_CONTRACT_STATUSES);

    UUID currentBedId = null;
    String currentBedCode = null;
    UUID currentRoomId = null;
    String currentRoomNumber = null;
    ContractDetailResponse currentContractResponse = null;

    if (activeContractOpt.isPresent()) {
      Contract activeContract = activeContractOpt.get();
      Bed bed = activeContract.getBed();
      if (bed != null) {
        currentBedId = bed.getId();
        currentBedCode = bed.getBedCode();
        Room room = bed.getRoom();
        if (room != null) {
          currentRoomId = room.getId();
          currentRoomNumber = room.getRoomNumber();
        }
      }
      currentContractResponse =
          toContractDetailResponse(activeContract, currentBedCode, currentRoomNumber);
    }

    List<Vehicle> vehicles = vehicleRepository.findByTenantIdAndDeletedAtIsNull(tenant.getId());
    List<VehicleResponse> vehicleResponses =
        vehicles.stream().map(v -> toVehicleResponse(v, tenant.getFullName())).toList();

    List<Contract> contractHistory = contractRepository.findByTenantId(tenant.getId());
    List<ContractSummaryResponse> contractHistoryResponses =
        contractHistory.stream().map(this::toContractSummaryResponse).toList();

    return TenantDetailResponse.builder()
        .id(tenant.getId())
        .userId(tenant.getUser() != null ? tenant.getUser().getId() : null)
        .tenantCode(tenant.getTenantCode())
        .fullName(tenant.getFullName())
        .gender(tenant.getGender())
        .idCardNumber(tenant.getIdCardNumber())
        .phone(tenant.getPhone())
        .email(tenant.getEmail())
        .permanentAddress(tenant.getPermanentAddress())
        .dateOfBirth(tenant.getDateOfBirth())
        .emergencyContactName(tenant.getEmergencyContactName())
        .emergencyContactPhone(tenant.getEmergencyContactPhone())
        .residentStatus(tenant.getResidentStatus())
        .currentBedId(currentBedId)
        .currentBedCode(currentBedCode)
        .currentRoomId(currentRoomId)
        .currentRoomNumber(currentRoomNumber)
        .currentContract(currentContractResponse)
        .vehicles(vehicleResponses)
        .contractHistory(contractHistoryResponses)
        .createdAt(tenant.getCreatedAt())
        .build();
  }

  private ContractDetailResponse toContractDetailResponse(
      Contract contract, String bedCode, String roomNumber) {
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
                            .fileType(doc.getFileType())
                            .fileSize(doc.getFileSize())
                            .documentCategory(doc.getDocumentCategory())
                            .uploadedAt(doc.getUploadedAt())
                            .build())
                .toList()
            : List.of();

    Bed bed = contract.getBed();
    Room room = bed != null ? bed.getRoom() : null;

    return ContractDetailResponse.builder()
        .id(contract.getId())
        .contractCode(contract.getContractCode())
        .tenantId(contract.getTenant() != null ? contract.getTenant().getId() : null)
        .tenantName(contract.getTenant() != null ? contract.getTenant().getFullName() : null)
        .tenantPhone(contract.getTenant() != null ? contract.getTenant().getPhone() : null)
        .bedId(bed != null ? bed.getId() : null)
        .bedCode(bedCode != null ? bedCode : (bed != null ? bed.getBedCode() : null))
        .roomId(room != null ? room.getId() : null)
        .roomNumber(roomNumber != null ? roomNumber : (room != null ? room.getRoomNumber() : null))
        .startDate(contract.getStartDate())
        .endDate(contract.getEndDate())
        .monthlyRent(contract.getMonthlyRent())
        .depositAmount(contract.getDepositAmount())
        .status(contract.getStatus())
        .contractDocumentUrl(contract.getContractDocumentUrl())
        .signedAt(contract.getSignedAt())
        .createdAt(contract.getCreatedAt())
        .documents(docResponses)
        .build();
  }

  private ContractSummaryResponse toContractSummaryResponse(Contract contract) {
    Bed bed = contract.getBed();
    Room room = bed != null ? bed.getRoom() : null;

    return ContractSummaryResponse.builder()
        .id(contract.getId())
        .contractCode(contract.getContractCode())
        .tenantId(contract.getTenant() != null ? contract.getTenant().getId() : null)
        .tenantName(contract.getTenant() != null ? contract.getTenant().getFullName() : null)
        .tenantPhone(contract.getTenant() != null ? contract.getTenant().getPhone() : null)
        .roomNumber(room != null ? room.getRoomNumber() : null)
        .bedCode(bed != null ? bed.getBedCode() : null)
        .startDate(contract.getStartDate())
        .endDate(contract.getEndDate())
        .monthlyRent(contract.getMonthlyRent())
        .depositAmount(contract.getDepositAmount())
        .status(contract.getStatus())
        .build();
  }

  private VehicleResponse toVehicleResponse(Vehicle vehicle, String tenantName) {
    return VehicleResponse.builder()
        .id(vehicle.getId())
        .tenantId(vehicle.getTenant() != null ? vehicle.getTenant().getId() : null)
        .tenantName(tenantName)
        .vehicleType(vehicle.getVehicleType())
        .licensePlate(vehicle.getLicensePlate())
        .brand(vehicle.getBrand())
        .color(vehicle.getColor())
        .cardCode(vehicle.getCardCode())
        .status(vehicle.getStatus())
        .registrationFrontUrl(vehicle.getRegistrationFrontUrl())
        .registrationBackUrl(vehicle.getRegistrationBackUrl())
        .createdAt(vehicle.getCreatedAt())
        .build();
  }
}
