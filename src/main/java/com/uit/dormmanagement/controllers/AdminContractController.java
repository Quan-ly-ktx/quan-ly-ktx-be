package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.contract.RenewContractRequest;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.base.SuccessResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractDocumentResponse;
import com.uit.dormmanagement.dtos.response.contract.ContractSummaryResponse;
import com.uit.dormmanagement.enums.ContractDocumentCategory;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.services.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(
    name = "Contracts (Admin)",
    description = "Contract management and lifecycle endpoints for managers (FR-10, FR-12, FR-13)")
@RestController
@RequestMapping("/api/v1/admin/contracts")
@RequiredArgsConstructor
public class AdminContractController extends AbstractBaseController {

  private final ContractService contractService;

  @Operation(
      summary = "Get contracts list",
      description = "Retrieve paged list of contracts with filters")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping
  public ResponseEntity<SingleResponse<PageResponse<ContractSummaryResponse>>> getContracts(
      Pageable pageable,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) ContractStatus status) {
    Page<ContractSummaryResponse> page = contractService.getContracts(pageable, search, status);
    return responseFactory.successPage(page, "Lấy danh sách hợp đồng thành công");
  }

  @Operation(
      summary = "Get contract detail",
      description = "Retrieve complete contract profile with documents")
  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/{id}")
  public ResponseEntity<SingleResponse<ContractDetailResponse>> getContractById(
      @PathVariable UUID id) {
    ContractDetailResponse response = contractService.getContractById(id);
    return responseFactory.success(response, "Lấy thông tin hợp đồng thành công");
  }

  @Operation(
      summary = "Renew contract",
      description = "Extend contract end date with signed annex documents (FR-12)")
  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping(value = "/{id}/renew", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<SingleResponse<ContractDetailResponse>> renewContract(
      @PathVariable UUID id,
      @ModelAttribute @Valid RenewContractRequest request,
      @RequestParam(value = "annexDocuments", required = false)
          List<MultipartFile> annexDocuments) {
    ContractDetailResponse response = contractService.renewContract(id, request, annexDocuments);
    return responseFactory.success(response, "Gia hạn hợp đồng thành công");
  }

  @Operation(
      summary = "Terminate contract (Checkout)",
      description = "Checkout resident and release bed atomically (FR-13)")
  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/{id}/terminate")
  public ResponseEntity<SingleResponse<ContractDetailResponse>> terminateContract(
      @PathVariable UUID id) {
    ContractDetailResponse response = contractService.terminateContract(id);
    return responseFactory.success(response, "Thanh lý hợp đồng và giải phóng giường thành công");
  }

  @Operation(
      summary = "Upload contract documents",
      description = "Upload multiple files (PDF/Images) for contract evidence (FR-10)")
  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<SingleResponse<List<ContractDocumentResponse>>> uploadDocuments(
      @PathVariable UUID id,
      @RequestParam("category") ContractDocumentCategory category,
      @RequestParam("files") List<MultipartFile> files) {
    List<ContractDocumentResponse> response = contractService.uploadDocuments(id, category, files);
    return responseFactory.success(response, "Tải lên tài liệu hợp đồng thành công");
  }

  @Operation(
      summary = "Delete contract document",
      description = "Remove a document from contract and storage")
  @PreAuthorize("hasRole('MANAGER')")
  @DeleteMapping("/{id}/documents/{documentId}")
  public ResponseEntity<SuccessResponse> deleteDocument(
      @PathVariable UUID id, @PathVariable UUID documentId) {
    contractService.deleteDocument(id, documentId);
    return responseFactory.success("Xóa tài liệu hợp đồng thành công");
  }
}
