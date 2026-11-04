package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.vehicle.CreateVehicleAdminRequest;
import com.uit.dormmanagement.dtos.request.vehicle.IssueCardRequest;
import com.uit.dormmanagement.dtos.request.vehicle.UpdateVehicleRequest;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.base.SuccessResponse;
import com.uit.dormmanagement.dtos.response.vehicle.VehicleResponse;
import com.uit.dormmanagement.enums.VehicleStatus;
import com.uit.dormmanagement.services.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Vehicles (Admin)",
    description = "Vehicle basement management endpoints for managers (FR-08)")
@RestController
@RequestMapping("/api/v1/admin/vehicles")
@RequiredArgsConstructor
public class AdminVehicleController extends AbstractBaseController {

  private final VehicleService vehicleService;

  @Operation(
      summary = "Get vehicles list",
      description = "Retrieve paged list of vehicles with filters by search query and status")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping
  public ResponseEntity<SingleResponse<PageResponse<VehicleResponse>>> getVehicles(
      Pageable pageable,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) VehicleStatus status) {
    Page<VehicleResponse> page = vehicleService.getVehicles(pageable, search, status);
    return responseFactory.successPage(page, "Lấy danh sách phương tiện thành công");
  }

  @Operation(summary = "Get vehicle by id", description = "Retrieve vehicle details by ID")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/{id}")
  public ResponseEntity<SingleResponse<VehicleResponse>> getVehicleById(@PathVariable UUID id) {
    VehicleResponse response = vehicleService.getVehicleById(id);
    return responseFactory.success(response, "Lấy thông tin phương tiện thành công");
  }

  @Operation(
      summary = "Create vehicle",
      description = "Admin manually registers a vehicle for a tenant")
  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping
  public ResponseEntity<SingleResponse<VehicleResponse>> createVehicle(
      @Valid @RequestBody CreateVehicleAdminRequest request) {
    VehicleResponse response = vehicleService.createVehicleAdmin(request);
    return responseFactory.success(HttpStatus.CREATED, response, "Thêm phương tiện thành công");
  }

  @Operation(
      summary = "Update vehicle",
      description = "Update vehicle plate, type, brand, and color")
  @PreAuthorize("hasRole('MANAGER')")
  @PutMapping("/{id}")
  public ResponseEntity<SingleResponse<VehicleResponse>> updateVehicle(
      @PathVariable UUID id, @Valid @RequestBody UpdateVehicleRequest request) {
    VehicleResponse response = vehicleService.updateVehicle(id, request);
    return responseFactory.success(response, "Cập nhật phương tiện thành công");
  }

  @Operation(
      summary = "Issue RFID card",
      description = "Assign physical card code to a pending vehicle")
  @PreAuthorize("hasRole('MANAGER')")
  @PatchMapping("/{id}/issue-card")
  public ResponseEntity<SingleResponse<VehicleResponse>> issueCard(
      @PathVariable UUID id, @Valid @RequestBody IssueCardRequest request) {
    VehicleResponse response = vehicleService.issueCard(id, request);
    return responseFactory.success(response, "Cấp thẻ gửi xe thành công");
  }

  @Operation(
      summary = "Revoke RFID card",
      description = "Revoke parking card and set status to REVOKED")
  @PreAuthorize("hasRole('MANAGER')")
  @PatchMapping("/{id}/revoke-card")
  public ResponseEntity<SingleResponse<VehicleResponse>> revokeCard(@PathVariable UUID id) {
    VehicleResponse response = vehicleService.revokeCard(id);
    return responseFactory.success(response, "Thu hồi thẻ xe thành công");
  }

  @Operation(summary = "Delete vehicle", description = "Soft delete a vehicle record")
  @PreAuthorize("hasRole('MANAGER')")
  @DeleteMapping("/{id}")
  public ResponseEntity<SuccessResponse> deleteVehicle(@PathVariable UUID id) {
    vehicleService.deleteVehicle(id);
    return responseFactory.success("Xóa phương tiện thành công");
  }
}
