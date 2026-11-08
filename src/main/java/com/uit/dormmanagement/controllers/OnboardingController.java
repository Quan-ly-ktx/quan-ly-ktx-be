package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.services.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Onboarding",
    description = "Direct onboarding and bed assignment endpoints for managers (FR-09)")
@RestController
@RequestMapping("/api/v1/admin/onboarding")
@RequiredArgsConstructor
public class OnboardingController extends AbstractBaseController {

  private final OnboardingService onboardingService;

  @Operation(
      summary = "Direct onboarding",
      description =
          "Direct onboarding 1-step transaction: create tenant, user, contract, and assign bed"
              + " (FR-09)")
  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping
  public ResponseEntity<SingleResponse<OnboardingResponse>> onboardTenant(
      @Valid @RequestBody OnboardingRequest request) {
    OnboardingResponse response = onboardingService.onboardTenant(request);
    return responseFactory.success(
        HttpStatus.CREATED, response, "Tiếp nhận và xếp phòng thành công");
  }
}
