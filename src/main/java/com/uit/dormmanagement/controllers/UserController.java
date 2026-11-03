package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.auth.CreateTechnicianRequest;
import com.uit.dormmanagement.dtos.request.auth.UpdateTechnicianRequest;
import com.uit.dormmanagement.dtos.request.auth.UpdateTechnicianStatusRequest;
import com.uit.dormmanagement.dtos.response.auth.TechnicianResponse;
import com.uit.dormmanagement.dtos.response.auth.UserProfileResponse;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "User profile and account management")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController extends AbstractBaseController {

  private final UserService userService;

  @Operation(
      summary = "Get current user profile",
      description = "Retrieve personal profile and role-specific details (Staff or Tenant)")
  @GetMapping("/me")
  public ResponseEntity<SingleResponse<UserProfileResponse>> getMe(
      @AuthenticationPrincipal UserPrincipal principal) {
    UserProfileResponse profile = userService.getCurrentUserProfile(principal.getId());
    return success(profile, "Lấy thông tin tài khoản thành công");
  }

  @Operation(
      summary = "Create technician account",
      description =
          "Create a new technician user and staff profile. Requires MANAGER role (FR-23).")
  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/technicians")
  public ResponseEntity<SingleResponse<TechnicianResponse>> createTechnician(
      @Valid @RequestBody CreateTechnicianRequest request) {
    TechnicianResponse response = userService.createTechnician(request);
    return created(response, "Tạo tài khoản kỹ thuật viên thành công");
  }

  @Operation(
      summary = "List technicians",
      description =
          "Retrieve paginated technicians list with search and active filters. Requires MANAGER role.")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/technicians")
  public ResponseEntity<SingleResponse<PageResponse<TechnicianResponse>>> getTechnicians(
      @RequestParam(required = false) Boolean active,
      @RequestParam(required = false) String search,
      Pageable pageable) {
    Page<TechnicianResponse> page = userService.getTechnicians(pageable, active, search);
    return successPage(page, "Lấy danh sách kỹ thuật viên thành công");
  }

  @Operation(
      summary = "Get technician details",
      description =
          "Retrieve details of a single technician by staff ID or user ID. Requires MANAGER role.")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/technicians/{id}")
  public ResponseEntity<SingleResponse<TechnicianResponse>> getTechnician(@PathVariable UUID id) {
    TechnicianResponse response = userService.getTechnicianById(id);
    return success(response, "Lấy thông tin kỹ thuật viên thành công");
  }

  @Operation(
      summary = "Update technician active status",
      description =
          "Lock or unlock a technician account. Locking revokes all active sessions immediately. Requires MANAGER role.")
  @PreAuthorize("hasRole('MANAGER')")
  @PatchMapping("/technicians/{id}/status")
  public ResponseEntity<SingleResponse<TechnicianResponse>> updateTechnicianStatus(
      @PathVariable UUID id, @Valid @RequestBody UpdateTechnicianStatusRequest request) {
    TechnicianResponse response = userService.updateTechnicianStatus(id, request);
    return success(response, "Cập nhật trạng thái kỹ thuật viên thành công");
  }

  @Operation(
      summary = "Update technician profile",
      description = "Update technician full name, email, or phone. Requires MANAGER role.")
  @PreAuthorize("hasRole('MANAGER')")
  @PutMapping("/technicians/{id}")
  public ResponseEntity<SingleResponse<TechnicianResponse>> updateTechnician(
      @PathVariable UUID id, @Valid @RequestBody UpdateTechnicianRequest request) {
    TechnicianResponse response = userService.updateTechnician(id, request);
    return success(response, "Cập nhật thông tin kỹ thuật viên thành công");
  }
}
