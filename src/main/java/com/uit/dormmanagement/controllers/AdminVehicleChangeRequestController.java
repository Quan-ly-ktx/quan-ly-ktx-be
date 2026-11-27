package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.vehicle.RejectVehicleChangeRequest;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleChangeRequestResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleEvidenceContent;
import com.uit.dormmanagement.enums.VehicleChangeRequestStatus;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.VehicleChangeRequestService;
import com.uit.dormmanagement.services.VehicleEvidenceAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Vehicle Change Requests (Admin)",
    description = "Admin vehicle replacement request processing endpoints (FR-08)")
@RestController
@RequestMapping("/api/v1/admin/vehicle-change-requests")
@RequiredArgsConstructor
public class AdminVehicleChangeRequestController extends AbstractBaseController {

  private final VehicleChangeRequestService vehicleChangeRequestService;
  private final VehicleEvidenceAccessService vehicleEvidenceAccessService;

  @Operation(
      summary = "Get vehicle change requests",
      description = "Retrieve paged list of vehicle change requests with filters")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping
  public ResponseEntity<SingleResponse<PageResponse<VehicleChangeRequestResponse>>> getRequests(
      Pageable pageable,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) VehicleChangeRequestStatus status) {
    Page<VehicleChangeRequestResponse> page =
        vehicleChangeRequestService.getAdminRequests(pageable, search, status);
    return responseFactory.successPage(page, "Lấy danh sách yêu cầu đổi xe thành công");
  }

  @Operation(
      summary = "Get change request detail",
      description = "Retrieve comparison and status of a change request for manager")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/{id}")
  public ResponseEntity<SingleResponse<VehicleChangeRequestResponse>> getRequestById(
      @PathVariable UUID id) {
    VehicleChangeRequestResponse response = vehicleChangeRequestService.getAdminRequestById(id);
    return responseFactory.success(response, "Lấy chi tiết yêu cầu đổi xe thành công");
  }

  @Operation(
      summary = "Approve vehicle change request",
      description =
          "Approve request: atomically replace old vehicle with successor using same RFID card")
  @PreAuthorize("hasRole('MANAGER')")
  @PatchMapping("/{id}/approve")
  public ResponseEntity<SingleResponse<VehicleChangeRequestResponse>> approveRequest(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    UUID managerUserId = principal != null ? principal.getId() : null;
    VehicleChangeRequestResponse response = vehicleChangeRequestService.approve(managerUserId, id);
    return responseFactory.success(response, "Duyệt yêu cầu đổi phương tiện thành công");
  }

  @Operation(
      summary = "Reject vehicle change request",
      description = "Reject vehicle change request with textual reason")
  @PreAuthorize("hasRole('MANAGER')")
  @PatchMapping("/{id}/reject")
  public ResponseEntity<SingleResponse<VehicleChangeRequestResponse>> rejectRequest(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody RejectVehicleChangeRequest request) {
    UUID managerUserId = principal != null ? principal.getId() : null;
    VehicleChangeRequestResponse response =
        vehicleChangeRequestService.reject(managerUserId, id, request.reason());
    return responseFactory.success(response, "Từ chối yêu cầu đổi phương tiện thành công");
  }

  @Operation(
      summary = "Get change request registration evidence",
      description = "Stream proposed vehicle registration photo (front/back) for manager")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/{requestId}/evidence/{side}")
  public ResponseEntity<Resource> getChangeRequestEvidence(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID requestId,
      @PathVariable String side) {
    UUID managerUserId = principal != null ? principal.getId() : null;
    VehicleEvidenceContent content =
        vehicleEvidenceAccessService.loadChangeRequestEvidence(
            managerUserId, true, requestId, side);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .contentLength(content.contentLength())
        .body(content.resource());
  }
}
