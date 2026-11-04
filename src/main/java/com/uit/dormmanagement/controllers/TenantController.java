package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.tenant.UpdateTenantRequest;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantDetailResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantSummaryResponse;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.services.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tenants", description = "Tenant profile management endpoints (FR-06)")
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantController extends AbstractBaseController {

  private final TenantService tenantService;

  @Operation(
      summary = "Get tenants list",
      description =
          "Retrieve paged list of tenants with filters by search keyword, room, and resident status")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping
  public ResponseEntity<SingleResponse<PageResponse<TenantSummaryResponse>>> getTenants(
      Pageable pageable,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) UUID roomId,
      @RequestParam(required = false) ResidentStatus residentStatus) {
    Page<TenantSummaryResponse> response =
        tenantService.getTenants(pageable, search, roomId, residentStatus);
    return responseFactory.successPage(response, "Lấy danh sách người thuê thành công");
  }

  @Operation(
      summary = "Get tenant detail",
      description =
          "Retrieve complete tenant profile including current bed, contract, vehicles, and contract history")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/{id}")
  public ResponseEntity<SingleResponse<TenantDetailResponse>> getTenantById(@PathVariable UUID id) {
    TenantDetailResponse response = tenantService.getTenantById(id);
    return responseFactory.success(response, "Lấy chi tiết hồ sơ người thuê thành công");
  }

  @Operation(
      summary = "Update tenant profile",
      description = "Update personal identification and emergency contact info for a tenant")
  @PreAuthorize("hasRole('MANAGER')")
  @PutMapping("/{id}")
  public ResponseEntity<SingleResponse<TenantDetailResponse>> updateTenant(
      @PathVariable UUID id, @Valid @RequestBody UpdateTenantRequest request) {
    TenantDetailResponse response = tenantService.updateTenant(id, request);
    return responseFactory.success(response, "Cập nhật thông tin người thuê thành công");
  }
}
