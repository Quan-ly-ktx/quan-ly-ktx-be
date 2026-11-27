package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleChangeRequest;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleChangeRequestResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleEvidenceContent;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.VehicleChangeRequestService;
import com.uit.dormmanagement.services.VehicleEvidenceAccessService;
import com.uit.dormmanagement.utils.StreamingResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(
    name = "Vehicle Change Requests (Tenant)",
    description = "Tenant vehicle replacement request endpoints (FR-08)")
@RestController
@RequestMapping("/api/v1/tenant/vehicle-change-requests")
@RequiredArgsConstructor
public class TenantVehicleChangeRequestController extends AbstractBaseController {

  private final VehicleChangeRequestService vehicleChangeRequestService;
  private final VehicleEvidenceAccessService vehicleEvidenceAccessService;

  @Operation(
      summary = "Submit vehicle change request",
      description = "Tenant requests to change their currently active vehicle")
  @PreAuthorize("hasRole('TENANT')")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<SingleResponse<VehicleChangeRequestResponse>> createChangeRequest(
      @AuthenticationPrincipal UserPrincipal principal,
      @ModelAttribute @Valid CreateVehicleChangeRequest request,
      @RequestParam(value = "frontImage", required = false) MultipartFile frontImage,
      @RequestParam(value = "backImage", required = false) MultipartFile backImage) {
    VehicleChangeRequestResponse response =
        vehicleChangeRequestService.create(principal.getId(), request, frontImage, backImage);
    return responseFactory.success(
        HttpStatus.CREATED, response, "Tạo yêu cầu đổi phương tiện thành công");
  }

  @Operation(
      summary = "Get my vehicle change requests",
      description = "Retrieve all vehicle change requests submitted by logged-in tenant")
  @PreAuthorize("hasRole('TENANT')")
  @GetMapping
  public ResponseEntity<SingleResponse<List<VehicleChangeRequestResponse>>> getMyRequests(
      @AuthenticationPrincipal UserPrincipal principal) {
    List<VehicleChangeRequestResponse> response =
        vehicleChangeRequestService.getTenantRequests(principal.getId());
    return responseFactory.success(response, "Lấy danh sách yêu cầu đổi xe thành công");
  }

  @Operation(
      summary = "Get change request detail",
      description = "Retrieve comparison and status of a change request")
  @PreAuthorize("hasRole('TENANT')")
  @GetMapping("/{id}")
  public ResponseEntity<SingleResponse<VehicleChangeRequestResponse>> getRequestById(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    VehicleChangeRequestResponse response =
        vehicleChangeRequestService.getTenantRequestById(principal.getId(), id);
    return responseFactory.success(response, "Lấy chi tiết yêu cầu đổi xe thành công");
  }

  @Operation(
      summary = "Get change request registration evidence",
      description = "Stream proposed vehicle registration photo (front/back) for owned request")
  @PreAuthorize("hasRole('TENANT')")
  @GetMapping("/{requestId}/evidence/{side}")
  public ResponseEntity<Resource> getChangeRequestEvidence(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID requestId,
      @PathVariable String side,
      HttpMethod httpMethod) {
    VehicleEvidenceContent content =
        vehicleEvidenceAccessService.loadChangeRequestEvidence(
            principal.getId(), false, requestId, side);
    return StreamingResponseUtils.ok(
        content.resource(), content.contentType(), content.contentLength(), httpMethod);
  }
}
