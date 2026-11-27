package com.uit.dormmanagement.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.finance.EstimatedNetProfitReportResponse;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportExportFile;
import com.uit.dormmanagement.enums.ExpenseType;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ExpenseErrorCode;
import com.uit.dormmanagement.services.FinancialReportExportService;
import com.uit.dormmanagement.services.FinancialReportService;
import com.uit.dormmanagement.services.impl.FinancialReportExportServiceImpl;
import com.uit.dormmanagement.services.impl.FinancialReportServiceImpl;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FinancialReportControllerTest {

  @Mock private FinancialReportService financialReportService;
  @Mock private FinancialReportExportService financialReportExportService;

  @InjectMocks private AdminFinancialReportController controller;

  private MockMvc mockMvc;
  private final ResponseFactory responseFactory = new ResponseFactory();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(controller, "responseFactory", responseFactory);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();
  }

  @Test
  @DisplayName(
      "GET /api/v1/admin/financial-reports/net-profit: supports default period, explicit period, and maps invalid period to 400")
  void getEstimatedNetProfitReport_supportsDefaultExplicitAndInvalidPeriod() throws Exception {
    EstimatedNetProfitReportResponse defaultReport =
        sampleReport("2026-10", new BigDecimal("4500000"), new BigDecimal("3000000"));
    EstimatedNetProfitReportResponse sepReport =
        sampleReport("2026-09", new BigDecimal("3000000"), new BigDecimal("1500000"));

    when(financialReportService.getEstimatedNetProfitReport(null)).thenReturn(defaultReport);
    when(financialReportService.getEstimatedNetProfitReport("2026-09")).thenReturn(sepReport);
    when(financialReportService.getEstimatedNetProfitReport("2026-13"))
        .thenThrow(new BaseAppException(ExpenseErrorCode.INVALID_REPORT_PERIOD));

    mockMvc
        .perform(get("/api/v1/admin/financial-reports/net-profit"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(
            jsonPath("$.message")
                .value("Lấy báo cáo thu chi và lợi nhuận ròng ước tính thành công"))
        .andExpect(jsonPath("$.data.period").value("2026-10"))
        .andExpect(
            jsonPath("$.data.label").value(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_LABEL))
        .andExpect(
            jsonPath("$.data.disclaimer")
                .value(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_DISCLAIMER))
        .andExpect(jsonPath("$.data.expectedRentRevenue").value(4500000))
        .andExpect(jsonPath("$.data.collectedRentRevenue").value(3000000))
        .andExpect(jsonPath("$.data.maintenancePartsCost").value(200000))
        .andExpect(jsonPath("$.data.facilityExpenseTotal").value(500000))
        .andExpect(jsonPath("$.data.totalExpenses").value(700000))
        .andExpect(jsonPath("$.data.estimatedNetProfit").value(2300000));

    mockMvc
        .perform(get("/api/v1/admin/financial-reports/net-profit").param("period", "2026-09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.period").value("2026-09"))
        .andExpect(jsonPath("$.data.collectedRentRevenue").value(1500000))
        .andExpect(jsonPath("$.data.estimatedNetProfit").value(800000));

    mockMvc
        .perform(get("/api/v1/admin/financial-reports/net-profit").param("period", "2026-13"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_INVALID_PERIOD"));

    verify(financialReportService).getEstimatedNetProfitReport(null);
    verify(financialReportService).getEstimatedNetProfitReport("2026-09");
    verify(financialReportService).getEstimatedNetProfitReport("2026-13");
  }

  @Test
  @DisplayName(
      "GET /api/v1/admin/financial-reports/net-profit/export: returns 400 on missing/invalid format and streams XLSX/PDF attachments")
  void exportEstimatedNetProfitReport_validatesFormatAndReturnsAttachmentHeaders()
      throws Exception {
    when(financialReportExportService.exportEstimatedNetProfitReport("2026-10", null))
        .thenThrow(new BaseAppException(ExpenseErrorCode.INVALID_EXPORT_FORMAT));
    when(financialReportExportService.exportEstimatedNetProfitReport("2026-10", "CSV"))
        .thenThrow(new BaseAppException(ExpenseErrorCode.INVALID_EXPORT_FORMAT));

    mockMvc
        .perform(
            get("/api/v1/admin/financial-reports/net-profit/export").param("period", "2026-10"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_EXPORT_FORMAT"));

    mockMvc
        .perform(
            get("/api/v1/admin/financial-reports/net-profit/export")
                .param("period", "2026-10")
                .param("format", "CSV"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("EXPENSE_400_EXPORT_FORMAT"));

    byte[] xlsxBytes = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x11, 0x22};
    when(financialReportExportService.exportEstimatedNetProfitReport("2026-10", "XLSX"))
        .thenReturn(
            FinancialReportExportFile.builder()
                .content(xlsxBytes)
                .contentType(FinancialReportExportServiceImpl.XLSX_CONTENT_TYPE)
                .filename("thu-chi-2026-10.xlsx")
                .build());

    String expectedXlsxDisposition =
        ContentDisposition.attachment()
            .filename("thu-chi-2026-10.xlsx", StandardCharsets.UTF_8)
            .build()
            .toString();

    mockMvc
        .perform(
            get("/api/v1/admin/financial-reports/net-profit/export")
                .param("period", "2026-10")
                .param("format", "XLSX"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_TYPE, FinancialReportExportServiceImpl.XLSX_CONTENT_TYPE))
        .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(xlsxBytes.length)))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedXlsxDisposition))
        .andExpect(content().bytes(xlsxBytes));

    byte[] pdfBytes = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    when(financialReportExportService.exportEstimatedNetProfitReport("2026-10", "PDF"))
        .thenReturn(
            FinancialReportExportFile.builder()
                .content(pdfBytes)
                .contentType(FinancialReportExportServiceImpl.PDF_CONTENT_TYPE)
                .filename("thu-chi-2026-10.pdf")
                .build());

    String expectedPdfDisposition =
        ContentDisposition.attachment()
            .filename("thu-chi-2026-10.pdf", StandardCharsets.UTF_8)
            .build()
            .toString();

    mockMvc
        .perform(
            get("/api/v1/admin/financial-reports/net-profit/export")
                .param("period", "2026-10")
                .param("format", "PDF"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_TYPE, FinancialReportExportServiceImpl.PDF_CONTENT_TYPE))
        .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdfBytes.length)))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, expectedPdfDisposition))
        .andExpect(content().bytes(pdfBytes));
  }

  private static EstimatedNetProfitReportResponse sampleReport(
      String period, BigDecimal expectedRevenue, BigDecimal collectedRevenue) {
    Map<ExpenseType, BigDecimal> breakdown = new LinkedHashMap<>();
    for (ExpenseType type : ExpenseType.values()) {
      breakdown.put(type, BigDecimal.ZERO);
    }
    breakdown.put(ExpenseType.ELECTRICITY, new BigDecimal("500000"));
    BigDecimal partsCost = new BigDecimal("200000");
    BigDecimal facilityTotal = new BigDecimal("500000");
    BigDecimal totalExpenses = partsCost.add(facilityTotal);

    return EstimatedNetProfitReportResponse.builder()
        .period(period)
        .periodStartDate(LocalDate.parse(period + "-01"))
        .periodEndDate(LocalDate.parse(period + "-01").withDayOfMonth(30))
        .label(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_LABEL)
        .disclaimer(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_DISCLAIMER)
        .expectedRentRevenue(expectedRevenue)
        .collectedRentRevenue(collectedRevenue)
        .maintenancePartsCost(partsCost)
        .facilityExpenseTotal(facilityTotal)
        .totalExpenses(totalExpenses)
        .estimatedNetProfit(collectedRevenue.subtract(totalExpenses))
        .expenseBreakdownByType(breakdown)
        .totalInvoiceCount(3)
        .paidInvoiceCount(2)
        .completedTicketCount(1)
        .facilityExpenseCount(1)
        .paidInvoices(List.of())
        .completedMaintenanceTickets(List.of())
        .facilityExpenses(List.of())
        .build();
  }
}
