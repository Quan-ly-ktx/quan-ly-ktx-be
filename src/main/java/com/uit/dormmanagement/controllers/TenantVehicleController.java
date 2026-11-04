package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.vehicle.TenantRegisterVehicleRequest;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
      summary = "Register personal vehicle",
      description =
          "Tenant registers a vehicle with front and back registration card photos (max 1 vehicle)")
  @PreAuthorize("hasRole('TENANT')")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<SingleResponse<VehicleResponse>> registerVehicle(
      @AuthenticationPrincipal UserPrincipal principal,
      @ModelAttribute @Valid TenantRegisterVehicleRequest request,
      @RequestParam("frontImage") MultipartFile frontImage,
      @RequestParam("backImage") MultipartFile backImage) {
    VehicleResponse response =
        vehicleService.registerVehicle(principal.getId(), request, frontImage, backImage);
    return responseFactory.success(HttpStatus.CREATED, response, "Đăng ký phương tiện thành công");
  }
}
