package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Contracts (Tenant)",
    description = "Tenant contract details and legal documents viewer")
@RestController
@RequestMapping("/api/v1/tenant/contracts")
@RequiredArgsConstructor
public class TenantContractController extends AbstractBaseController {

  private final ContractService contractService;

  @Operation(
      summary = "Get my contract",
      description = "Retrieve current active contract and documents of logged-in tenant")
  @PreAuthorize("hasRole('TENANT')")
  @GetMapping("/me")
  public ResponseEntity<SingleResponse<ContractDetailResponse>> getMyContract(
      @AuthenticationPrincipal UserPrincipal principal) {
    ContractDetailResponse response = contractService.getMyContract(principal.getId());
    return responseFactory.success(response, "Lấy thông tin hợp đồng thành công");
  }
}
