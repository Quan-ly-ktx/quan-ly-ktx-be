package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.tenant.ManagerUpdateTenantRequest;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.auth.OneTimePasswordResponse;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantDetailResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantSummaryResponse;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tenant Management", description = "Endpoints for managing tenants and their profiles")
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

  private final TenantService tenantService;
  private final ResponseFactory responseFactory;

  @Operation(
      summary = "Get list of tenants",
      description =
          "Retrieve paginated list of tenants with filters for search, room, and status (FR-05)")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping
  public ResponseEntity<SingleResponse<PageResponse<TenantSummaryResponse>>> getTenants(
      Pageable pageable,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) UUID roomId,
      @RequestParam(required = false) ResidentStatus status) {
    return responseFactory.successPage(
        tenantService.getTenants(pageable, search, roomId, status),
        "Lấy danh sách khách thuê thành công");
  }

  @Operation(
      summary = "Get tenant details",
      description =
          "Retrieve comprehensive tenant details including contract, bed, room, and vehicle information (FR-05)")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/{id}")
  public ResponseEntity<SingleResponse<TenantDetailResponse>> getTenantById(@PathVariable UUID id) {
    TenantDetailResponse response = tenantService.getTenantById(id);
    return responseFactory.success(response, "Lấy thông tin chi tiết khách thuê thành công");
  }

  @Operation(
      summary = "Update tenant profile",
      description =
          "Update tenant personal information, contact details, and identity fields (FR-06)")
  @PreAuthorize("hasRole('MANAGER')")
  @PutMapping("/{id}")
  public ResponseEntity<SingleResponse<TenantDetailResponse>> updateTenant(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID id,
      @Valid @RequestBody ManagerUpdateTenantRequest request) {
    UUID managerUserId = principal != null ? principal.getId() : null;
    TenantDetailResponse response = tenantService.updateTenantAsManager(managerUserId, id, request);
    return responseFactory.success(response, "Cập nhật thông tin khách thuê thành công");
  }

  @Operation(
      summary = "Reset tenant password",
      description = "Manager resets tenant password to a server-generated random password (FR-24)")
  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/{id}/reset-password")
  public ResponseEntity<SingleResponse<OneTimePasswordResponse>> resetTenantPassword(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
    UUID managerUserId = principal != null ? principal.getId() : null;
    OneTimePasswordResponse response =
        tenantService.resetTenantPasswordAsManager(managerUserId, id);
    return responseFactory.success(response, "Đặt lại mật khẩu cho khách thuê thành công");
  }
}
