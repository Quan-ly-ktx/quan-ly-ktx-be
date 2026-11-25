package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleChangeRequest;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleChangeRequestResponse;
import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface VehicleChangeRequestService {

  VehicleChangeRequestResponse create(
      UUID tenantUserId,
      CreateVehicleChangeRequest request,
      MultipartFile front,
      MultipartFile back);

  List<VehicleChangeRequestResponse> getTenantRequests(UUID tenantUserId);

  VehicleChangeRequestResponse getTenantRequestById(UUID tenantUserId, UUID requestId);

  Page<VehicleChangeRequestResponse> getAdminRequests(
      Pageable pageable, String search, VehicleChangeRequestStatus status);

  VehicleChangeRequestResponse getAdminRequestById(UUID requestId);

  VehicleChangeRequestResponse approve(UUID managerUserId, UUID requestId);

  VehicleChangeRequestResponse reject(UUID managerUserId, UUID requestId, String reason);
}
