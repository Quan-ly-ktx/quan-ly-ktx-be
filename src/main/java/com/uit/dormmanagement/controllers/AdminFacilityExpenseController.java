package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.request.expense.CreateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.request.expense.UpdateFacilityExpenseRequest;
import com.uit.dormmanagement.dtos.response.base.PageResponse;
import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.base.SuccessResponse;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseReceiptContent;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseResponse;
import com.uit.dormmanagement.enums.ExpenseType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ExpenseErrorCode;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.FacilityExpenseService;
import com.uit.dormmanagement.utils.StreamingResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Admin Facility Expenses", description = "Quản lý chi phí vận hành cơ sở (FR-14)")
@RestController
@RequestMapping("/api/v1/admin/expenses")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
public class AdminFacilityExpenseController extends AbstractBaseController {

  private final FacilityExpenseService facilityExpenseService;

  @Operation(
      summary = "Get facility expenses list",
      description =
          "Retrieve paged list of facility expenses with optional period (YYYY-MM), type, and search filters")
  @GetMapping
  public ResponseEntity<SingleResponse<PageResponse<FacilityExpenseResponse>>> getExpenses(
      @RequestParam(required = false) String period,
      @RequestParam(required = false) ExpenseType expenseType,
      @RequestParam(required = false) String search,
      Pageable pageable) {
    PageResponse<FacilityExpenseResponse> page =
        facilityExpenseService.getExpenses(period, expenseType, search, pageable);
    return responseFactory.success(page, "Lấy danh sách chi phí vận hành thành công");
  }

  @Operation(
      summary = "Get facility expense detail",
      description = "Retrieve details of a specific facility expense by ID")
  @GetMapping("/{id}")
  public ResponseEntity<SingleResponse<FacilityExpenseResponse>> getExpenseById(
      @PathVariable UUID id) {
    FacilityExpenseResponse response = facilityExpenseService.getExpenseById(id);
    return responseFactory.success(response, "Lấy chi tiết chi phí vận hành thành công");
  }

  @Operation(
      summary = "Create facility expense",
      description = "Record a new facility operating expense (FR-14)")
  @PostMapping
  public ResponseEntity<SingleResponse<FacilityExpenseResponse>> createExpense(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateFacilityExpenseRequest request) {
    UUID managerUserId = principal != null ? principal.getId() : null;
    FacilityExpenseResponse response = facilityExpenseService.createExpense(managerUserId, request);
    return created(response, "Tạo chi phí vận hành thành công");
  }

  @Operation(
      summary = "Update facility expense",
      description = "Update an existing facility operating expense by ID")
  @PutMapping("/{id}")
  public ResponseEntity<SingleResponse<FacilityExpenseResponse>> updateExpense(
      @PathVariable UUID id, @Valid @RequestBody UpdateFacilityExpenseRequest request) {
    FacilityExpenseResponse response = facilityExpenseService.updateExpense(id, request);
    return responseFactory.success(response, "Cập nhật chi phí vận hành thành công");
  }

  @Operation(
      summary = "Delete facility expense",
      description = "Delete a facility operating expense and schedule receipt cleanup if present")
  @DeleteMapping("/{id}")
  public ResponseEntity<SuccessResponse> deleteExpense(@PathVariable UUID id) {
    facilityExpenseService.deleteExpense(id);
    return responseFactory.success("Xóa chi phí vận hành thành công");
  }

  @Operation(
      summary = "Upload facility expense receipt",
      description =
          "Upload or replace private receipt/document (JPG/PNG/WEBP/PDF, max 5 MB) for a facility expense")
  @PostMapping(value = "/{id}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<SingleResponse<FacilityExpenseResponse>> uploadReceipt(
      @PathVariable UUID id, @RequestParam(value = "file", required = false) MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BaseAppException(ExpenseErrorCode.RECEIPT_FILE_REQUIRED);
    }
    FacilityExpenseResponse response = facilityExpenseService.uploadReceipt(id, file);
    return responseFactory.success(response, "Tải lên hóa đơn/chứng từ thành công");
  }

  @Operation(
      summary = "Delete facility expense receipt",
      description = "Remove the attached receipt/document from a facility expense")
  @DeleteMapping("/{id}/receipt")
  public ResponseEntity<SingleResponse<FacilityExpenseResponse>> deleteReceipt(
      @PathVariable UUID id) {
    FacilityExpenseResponse response = facilityExpenseService.deleteReceipt(id);
    return responseFactory.success(response, "Xóa hóa đơn/chứng từ thành công");
  }

  @Operation(
      summary = "Stream facility expense receipt content",
      description = "Stream private receipt/document content inline for manager review")
  @RequestMapping(
      value = "/{id}/receipt/content",
      method = {RequestMethod.GET, RequestMethod.HEAD})
  public ResponseEntity<Resource> getReceiptContent(
      @PathVariable UUID id, @Parameter(hidden = true) HttpMethod httpMethod) {
    FacilityExpenseReceiptContent content = facilityExpenseService.loadReceiptContent(id);
    return StreamingResponseUtils.okInline(
        content.resource(),
        content.contentType(),
        content.contentLength(),
        content.filename(),
        httpMethod);
  }
}
