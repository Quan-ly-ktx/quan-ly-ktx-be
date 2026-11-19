package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleAdminRequest;
import com.uit.dormmanagement.dtos.request.vehicle.IssueCardRequest;
import com.uit.dormmanagement.dtos.request.vehicle.TenantRegisterVehicleRequest;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.enums.VehicleStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface VehicleService {

  Page<VehicleResponse> getVehicles(Pageable pageable, String search, VehicleStatus status);

  VehicleResponse getVehicleById(UUID id);

  VehicleResponse createVehicleAdmin(
      CreateVehicleAdminRequest request, MultipartFile front, MultipartFile back);

  VehicleResponse createVehicleAdmin(CreateVehicleAdminRequest request);

  VehicleResponse issueCard(UUID managerUserId, UUID id, IssueCardRequest request);

  VehicleResponse issueCard(UUID id, IssueCardRequest request);

  VehicleResponse rejectVehicle(UUID managerUserId, UUID id, String reason);

  VehicleResponse revokeCard(UUID managerUserId, UUID id);

  VehicleResponse revokeCard(UUID id);

  void deleteVehicle(UUID id);

  VehicleResponse getMyVehicle(UUID userId);

  List<VehicleResponse> getVehicleHistory(UUID userId);

  VehicleResponse registerVehicle(
      UUID userId,
      TenantRegisterVehicleRequest request,
      MultipartFile frontImage,
      MultipartFile backImage);
}
