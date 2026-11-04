package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleAdminRequest;
import com.uit.dormmanagement.dtos.request.vehicle.IssueCardRequest;
import com.uit.dormmanagement.dtos.request.vehicle.TenantRegisterVehicleRequest;
import com.uit.dormmanagement.dtos.request.vehicle.UpdateVehicleRequest;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.enums.VehicleStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface VehicleService {

  Page<VehicleResponse> getVehicles(Pageable pageable, String search, VehicleStatus status);

  VehicleResponse getVehicleById(UUID id);

  VehicleResponse createVehicleAdmin(CreateVehicleAdminRequest request);

  VehicleResponse updateVehicle(UUID id, UpdateVehicleRequest request);

  VehicleResponse issueCard(UUID id, IssueCardRequest request);

  VehicleResponse revokeCard(UUID id);

  void deleteVehicle(UUID id);

  VehicleResponse getMyVehicle(UUID userId);

  VehicleResponse registerVehicle(
      UUID userId,
      TenantRegisterVehicleRequest request,
      MultipartFile frontImage,
      MultipartFile backImage);
}
