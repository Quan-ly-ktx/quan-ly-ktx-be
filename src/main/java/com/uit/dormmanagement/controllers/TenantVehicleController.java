package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.vehicle.TenantRegisterVehicleRequest;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleEvidenceContent;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.VehicleEvidenceAccessService;
import com.uit.dormmanagement.services.VehicleService;
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
    name = "Vehicles (Tenant)",
    description = "Tenant personal vehicle registration and card tracking (FR-08)")
@RestController
@RequestMapping("/api/v1/tenant/vehicles")
@RequiredArgsConstructor
public class TenantVehicleController extends AbstractBaseController {

  private final VehicleService vehicleService;
  private final VehicleEvidenceAccessService vehicleEvidenceAccessService;

  @Operation(
      summary = "Get my vehicle",
      description = "Retrieve current active or pending vehicle of logged-in tenant")
  @PreAuthorize("hasRole('TENANT')")
  @GetMapping("/me")
  public ResponseEntity<SingleResponse<VehicleResponse>> getMyVehicle(
      @AuthenticationPrincipal UserPrincipal principal) {
    VehicleResponse response = vehicleService.getMyVehicle(principal.getId());
    return responseFactory.success(response, "Lấy thông tin phương tiện thành công");
  }

  @Operation(
      summary = "Get vehicle history",
      description = "Retrieve all owned vehicles of logged-in tenant including history")
  @PreAuthorize("hasRole('TENANT')")
  @GetMapping
  public ResponseEntity<SingleResponse<List<VehicleResponse>>> getVehicleHistory(
      @AuthenticationPrincipal UserPrincipal principal) {
    List<VehicleResponse> response = vehicleService.getVehicleHistory(principal.getId());
    return responseFactory.success(response, "Lấy lịch sử phương tiện thành công");
  }

  @Operation(
      summary = "Register personal vehicle",
      description =
          "Tenant registers a vehicle with front and back registration card photos (max 1 vehicle)")
  @PreAuthorize("hasRole('TENANT')")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<SingleResponse<VehicleResponse>> registerVehicle(
      @AuthenticationPrincipal UserPrincipal principal,
      @ModelAttribute @Valid TenantRegisterVehicleRequest request,
      @RequestParam(value = "frontImage", required = false) MultipartFile frontImage,
      @RequestParam(value = "backImage", required = false) MultipartFile backImage) {
    VehicleResponse response =
        vehicleService.registerVehicle(principal.getId(), request, frontImage, backImage);
    return responseFactory.success(HttpStatus.CREATED, response, "Đăng ký phương tiện thành công");
  }

  @Operation(
      summary = "Get vehicle registration evidence",
      description = "Stream vehicle registration photo (front/back) for owned vehicle")
  @PreAuthorize("hasRole('TENANT')")
  @GetMapping("/{vehicleId}/evidence/{side}")
  public ResponseEntity<Resource> getVehicleEvidence(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID vehicleId,
      @PathVariable String side,
      HttpMethod httpMethod) {
    VehicleEvidenceContent content =
        vehicleEvidenceAccessService.loadVehicleEvidence(principal.getId(), false, vehicleId, side);
    return StreamingResponseUtils.ok(
        content.resource(), content.contentType(), content.contentLength(), httpMethod);
  }
}
