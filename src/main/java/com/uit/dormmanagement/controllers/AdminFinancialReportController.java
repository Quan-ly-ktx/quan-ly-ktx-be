package com.uit.dormmanagement.controllers;

import com.uit.dormmanagement.dtos.response.base.SingleResponse;
import com.uit.dormmanagement.dtos.response.finance.EstimatedNetProfitReportResponse;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportExportFile;
import com.uit.dormmanagement.services.FinancialReportExportService;
import com.uit.dormmanagement.services.FinancialReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Admin Financial Reports",
    description = "Báo cáo thu - chi và lợi nhuận ròng ước tính (FR-14)")
@RestController
@RequestMapping("/api/v1/admin/financial-reports")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
public class AdminFinancialReportController extends AbstractBaseController {

  private final FinancialReportService financialReportService;
  private final FinancialReportExportService financialReportExportService;

  @Operation(
      summary = "Get estimated net profit report",
      description =
          "Retrieve monthly collected rent revenue, maintenance parts cost, facility expenses, and estimated net profit (FR-14)")
  @GetMapping("/net-profit")
  public ResponseEntity<SingleResponse<EstimatedNetProfitReportResponse>>
      getEstimatedNetProfitReport(@RequestParam(required = false) String period) {
    EstimatedNetProfitReportResponse response =
        financialReportService.getEstimatedNetProfitReport(period);
    return responseFactory.success(
        response, "Lấy báo cáo thu chi và lợi nhuận ròng ước tính thành công");
  }

  @Operation(
      summary = "Export estimated net profit report",
      description = "Export monthly estimated net profit report in XLSX or PDF format (FR-14)")
  @GetMapping("/net-profit/export")
  public ResponseEntity<byte[]> exportEstimatedNetProfitReport(
      @RequestParam(required = false) String period,
      @RequestParam(required = false) String format) {
    FinancialReportExportFile file =
        financialReportExportService.exportEstimatedNetProfitReport(period, format);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .contentLength(file.content().length)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(file.content());
  }
}
