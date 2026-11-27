package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.tenant.TenantContactUpdateRequest;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.auth.UserProfileResponse;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tenant Self-Service", description = "Endpoints for tenant contact self-service")
@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
public class TenantProfileController {

  private final TenantService tenantService;
  private final ResponseFactory responseFactory;

  @Operation(
      summary = "Update own contact information",
      description =
          "Update tenant's own phone, permanent address, and emergency contact details (FR-06, FR-07)")
  @PreAuthorize("hasRole('TENANT')")
  @PatchMapping("/profile")
  public ResponseEntity<SingleResponse<UserProfileResponse>> updateOwnContact(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody TenantContactUpdateRequest request) {
    UserProfileResponse response = tenantService.updateOwnContact(principal.getId(), request);
    return responseFactory.success(response, "Cập nhật thông tin liên lạc thành công");
  }
}
