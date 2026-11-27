package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseResponse;
import com.uit.dormmanagement.dtos.response.finance.EstimatedNetProfitReportResponse;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportExportFile;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportMaintenanceTicketItem;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportPaidInvoiceItem;
import com.uit.dormmanagement.enums.ExpenseType;
import com.uit.dormmanagement.enums.PaymentMethod;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ExpenseErrorCode;
import com.uit.dormmanagement.services.impl.FinancialReportExportServiceImpl;
import com.uit.dormmanagement.services.impl.FinancialReportServiceImpl;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialReportExportServiceImplTest {

  @Mock private FinancialReportService financialReportService;

  private FinancialReportExportServiceImpl exportService;

  @BeforeEach
  void setUp() {
    exportService = new FinancialReportExportServiceImpl(financialReportService);
  }

  @Test
  @DisplayName("Null, blank, or unsupported export format throws 400 EXPENSE_400_EXPORT_FORMAT")
  void invalidOrBlankFormat_throwsInvalidExportFormat() {
    for (String format : List.of("", "   ", "csv", "xls", "excel", "docx")) {
      assertThatThrownBy(() -> exportService.exportEstimatedNetProfitReport("2026-10", format))
          .isInstanceOf(BaseAppException.class)
          .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.INVALID_EXPORT_FORMAT);
    }
    assertThatThrownBy(() -> exportService.exportEstimatedNetProfitReport("2026-10", null))
        .isInstanceOf(BaseAppException.class)
        .hasFieldOrPropertyWithValue("errorCode", ExpenseErrorCode.INVALID_EXPORT_FORMAT);

    verifyNoInteractions(financialReportService);
  }

  @Test
  @DisplayName("Case-insensitive xlsx and pdf format names are accepted")
  void caseInsensitiveFormats_areAccepted() {
    EstimatedNetProfitReportResponse emptyReport = buildEmptyReport("2026-10");
    when(financialReportService.getEstimatedNetProfitReport("2026-10")).thenReturn(emptyReport);

    FinancialReportExportFile xlsxLower =
        exportService.exportEstimatedNetProfitReport("2026-10", " xlsx ");
    assertThat(xlsxLower.contentType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertThat(xlsxLower.filename()).isEqualTo("thu-chi-2026-10.xlsx");
    assertThat(xlsxLower.content()).isNotEmpty();

    FinancialReportExportFile pdfMixed =
        exportService.exportEstimatedNetProfitReport("2026-10", " Pdf ");
    assertThat(pdfMixed.contentType()).isEqualTo("application/pdf");
    assertThat(pdfMixed.filename()).isEqualTo("thu-chi-2026-10.pdf");
    assertThat(new String(pdfMixed.content(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

    verify(financialReportService, org.mockito.Mockito.times(2))
        .getEstimatedNetProfitReport("2026-10");
  }

  @Test
  @DisplayName(
      "XLSX export builds 4 sheets, numeric KPIs including negative profit, exact disclaimer, and neutralizes formula injection")
  void xlsxExport_buildsAllSheetsNumericKpisAndSanitizesFormulas() throws Exception {
    EstimatedNetProfitReportResponse report = buildRichSampleReport();
    when(financialReportService.getEstimatedNetProfitReport("2026-10")).thenReturn(report);

    FinancialReportExportFile file =
        exportService.exportEstimatedNetProfitReport("2026-10", "XLSX");

    assertThat(file.contentType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    assertThat(file.filename()).isEqualTo("thu-chi-2026-10.xlsx");

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
      assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
      assertThat(workbook.getSheetName(0)).isEqualTo("Tong hop thu chi");
      assertThat(workbook.getSheetName(1)).isEqualTo("Chi tiet chi phi");
      assertThat(workbook.getSheetName(2)).isEqualTo("Hoa don da thu");
      assertThat(workbook.getSheetName(3)).isEqualTo("Sua chua hoan thanh");

      Sheet summarySheet = workbook.getSheet("Tong hop thu chi");
      assertNumericCellEquals(summarySheet, 6, 1, 6_000_000d);
      assertNumericCellEquals(summarySheet, 6, 2, 4d);
      assertNumericCellEquals(summarySheet, 7, 1, 3_000_000d);
      assertNumericCellEquals(summarySheet, 7, 2, 2d);
      assertNumericCellEquals(summarySheet, 8, 1, 500_000d);
      assertNumericCellEquals(summarySheet, 8, 2, 2d);
      assertNumericCellEquals(summarySheet, 9, 1, 5_500_000d);
      assertNumericCellEquals(summarySheet, 9, 2, 3d);
      assertNumericCellEquals(summarySheet, 10, 1, 6_000_000d);
      assertThat(summarySheet.getRow(11).getCell(0).getStringCellValue())
          .isEqualTo(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_LABEL);
      assertNumericCellEquals(summarySheet, 11, 1, -3_000_000d);

      // 5 ExpenseType breakdown rows (rows 15..19)
      assertNumericCellEquals(summarySheet, 15, 2, 2_000_000d);
      assertNumericCellEquals(summarySheet, 16, 2, 0d);
      assertNumericCellEquals(summarySheet, 17, 2, 500_000d);
      assertNumericCellEquals(summarySheet, 18, 2, 3_000_000d);
      assertNumericCellEquals(summarySheet, 19, 2, 0d);

      assertThat(summarySheet.getRow(21).getCell(1).getStringCellValue())
          .isEqualTo(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_DISCLAIMER);

      // Formula injection checks across detail sheets
      Sheet expenseSheet = workbook.getSheet("Chi tiet chi phi");
      Row firstExpenseRow = expenseSheet.getRow(1);
      assertThat(firstExpenseRow.getCell(2).getStringCellValue()).isEqualTo("'=SUM(A1:A2)");
      assertThat(firstExpenseRow.getCell(3).getStringCellValue()).isEqualTo("'+1+1");
      assertNumericCellEquals(expenseSheet, 1, 4, 2_000_000d);
      assertThat(firstExpenseRow.getCell(5).getStringCellValue()).isEqualTo("'@admin");
      assertThat(firstExpenseRow.getCell(6).getStringCellValue()).isEqualTo("'-cmd");

      Row secondExpenseRow = expenseSheet.getRow(2);
      assertThat(secondExpenseRow.getCell(2).getStringCellValue()).isEqualTo("'\t=1");
      assertThat(secondExpenseRow.getCell(3).getStringCellValue()).isEqualTo("'\r=1");

      Sheet invoiceSheet = workbook.getSheet("Hoa don da thu");
      assertThat(invoiceSheet.getRow(1).getCell(0).getStringCellValue())
          .isEqualTo("'=HYPERLINK(\"http://evil\")");
      assertThat(invoiceSheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Nguyễn Văn A");
      assertNumericCellEquals(invoiceSheet, 1, 5, 1_500_000d);

      Sheet ticketSheet = workbook.getSheet("Sua chua hoan thanh");
      assertThat(ticketSheet.getRow(1).getCell(1).getStringCellValue())
          .isEqualTo("'+cmd|'/C calc'!A0");
      assertNumericCellEquals(ticketSheet, 1, 4, 500_000d);
      assertNumericCellEquals(ticketSheet, 2, 4, 0d);
    }

    assertThat(FinancialReportExportServiceImpl.sanitizeSpreadsheetText("=SUM(A1:A2)"))
        .isEqualTo("'=SUM(A1:A2)");
    assertThat(FinancialReportExportServiceImpl.sanitizeSpreadsheetText("+1")).isEqualTo("'+1");
    assertThat(FinancialReportExportServiceImpl.sanitizeSpreadsheetText("-1")).isEqualTo("'-1");
    assertThat(FinancialReportExportServiceImpl.sanitizeSpreadsheetText("@cmd")).isEqualTo("'@cmd");
    assertThat(FinancialReportExportServiceImpl.sanitizeSpreadsheetText("\t=1")).isEqualTo("'\t=1");
    assertThat(FinancialReportExportServiceImpl.sanitizeSpreadsheetText("\r=1")).isEqualTo("'\r=1");
    assertThat(FinancialReportExportServiceImpl.sanitizeSpreadsheetText("Tiền điện"))
        .isEqualTo("Tiền điện");
    assertThat(FinancialReportExportServiceImpl.sanitizeSpreadsheetText("")).isEmpty();
    assertThat(FinancialReportExportServiceImpl.sanitizeSpreadsheetText(null)).isNull();
  }

  @Test
  @DisplayName(
      "PDF export generates valid PDF embedding NotoSans with clean Vietnamese diacritics and exact disclaimer")
  void pdfExport_generatesValidPdfWithVietnameseTextAndDisclaimer() throws Exception {
    EstimatedNetProfitReportResponse report = buildRichSampleReport();
    when(financialReportService.getEstimatedNetProfitReport("2026-10")).thenReturn(report);

    FinancialReportExportFile file = exportService.exportEstimatedNetProfitReport("2026-10", "pdf");

    assertThat(file.contentType()).isEqualTo("application/pdf");
    assertThat(file.filename()).isEqualTo("thu-chi-2026-10.pdf");
    assertThat(file.content().length).isGreaterThan(100);
    assertThat(new String(file.content(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

    try (PdfReader reader = new PdfReader(file.content())) {
      assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
      PdfTextExtractor extractor = new PdfTextExtractor(reader);
      StringBuilder rawText = new StringBuilder();
      for (int page = 1; page <= reader.getNumberOfPages(); page++) {
        rawText.append(extractor.getTextFromPage(page)).append('\n');
      }
      String normalizedText =
          Normalizer.normalize(rawText.toString(), Normalizer.Form.NFC)
              .replaceAll("\\s+", " ")
              .trim();

      assertThat(normalizedText).contains("2026-10");
      assertThat(normalizedText).contains(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_LABEL);
      assertThat(normalizedText)
          .contains(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_DISCLAIMER);
      assertThat(normalizedText).contains("Điện EVN");
      assertThat(normalizedText).contains("Nguyễn Văn A");
      assertThat(normalizedText).contains("Sửa ống nước tầng 3");
      assertThat(normalizedText).contains("-3.000.000 đ");
    }
  }

  private static void assertNumericCellEquals(
      Sheet sheet, int rowIndex, int colIndex, double expected) {
    Cell cell = sheet.getRow(rowIndex).getCell(colIndex);
    assertThat(cell).isNotNull();
    assertThat(cell.getCellType()).isEqualTo(CellType.NUMERIC);
    assertThat(cell.getNumericCellValue()).isEqualTo(expected);
  }

  private static EstimatedNetProfitReportResponse buildEmptyReport(String period) {
    Map<ExpenseType, BigDecimal> breakdown = new LinkedHashMap<>();
    for (ExpenseType type : ExpenseType.values()) {
      breakdown.put(type, BigDecimal.ZERO);
    }
    return EstimatedNetProfitReportResponse.builder()
        .period(period)
        .periodStartDate(LocalDate.of(2026, 10, 1))
        .periodEndDate(LocalDate.of(2026, 10, 31))
        .label(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_LABEL)
        .disclaimer(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_DISCLAIMER)
        .expectedRentRevenue(BigDecimal.ZERO)
        .collectedRentRevenue(BigDecimal.ZERO)
        .maintenancePartsCost(BigDecimal.ZERO)
        .facilityExpenseTotal(BigDecimal.ZERO)
        .totalExpenses(BigDecimal.ZERO)
        .estimatedNetProfit(BigDecimal.ZERO)
        .expenseBreakdownByType(breakdown)
        .totalInvoiceCount(0)
        .paidInvoiceCount(0)
        .completedTicketCount(0)
        .facilityExpenseCount(0)
        .paidInvoices(List.of())
        .completedMaintenanceTickets(List.of())
        .facilityExpenses(List.of())
        .build();
  }

  private static EstimatedNetProfitReportResponse buildRichSampleReport() {
    Map<ExpenseType, BigDecimal> breakdown = new LinkedHashMap<>();
    breakdown.put(ExpenseType.ELECTRICITY, new BigDecimal("2000000"));
    breakdown.put(ExpenseType.WATER, BigDecimal.ZERO);
    breakdown.put(ExpenseType.INTERNET, new BigDecimal("500000"));
    breakdown.put(ExpenseType.SALARY, new BigDecimal("3000000"));
    breakdown.put(ExpenseType.OTHER, BigDecimal.ZERO);

    List<FacilityExpenseResponse> expenses =
        List.of(
            FacilityExpenseResponse.builder()
                .id(UUID.randomUUID())
                .expenseType(ExpenseType.ELECTRICITY)
                .title("=SUM(A1:A2)")
                .amount(new BigDecimal("2000000"))
                .expenseDate(LocalDate.of(2026, 10, 10))
                .invoiceCode("+1+1")
                .notes("-cmd")
                .recordedByStaffId(UUID.randomUUID())
                .recordedByStaffName("@admin")
                .build(),
            FacilityExpenseResponse.builder()
                .id(UUID.randomUUID())
                .expenseType(ExpenseType.INTERNET)
                .title("\t=1")
                .amount(new BigDecimal("500000"))
                .expenseDate(LocalDate.of(2026, 10, 12))
                .invoiceCode("\r=1")
                .notes("Cáp quang tháng 10")
                .recordedByStaffId(UUID.randomUUID())
                .recordedByStaffName("Trần Quản Lý")
                .build(),
            FacilityExpenseResponse.builder()
                .id(UUID.randomUUID())
                .expenseType(ExpenseType.SALARY)
                .title("Lương nhân viên tháng 10")
                .amount(new BigDecimal("3000000"))
                .expenseDate(LocalDate.of(2026, 10, 25))
                .invoiceCode("SAL-2026-10")
                .notes(null)
                .recordedByStaffId(UUID.randomUUID())
                .recordedByStaffName("Trần Quản Lý")
                .build());

    List<FinancialReportPaidInvoiceItem> paidInvoices =
        List.of(
            FinancialReportPaidInvoiceItem.builder()
                .invoiceId(UUID.randomUUID())
                .invoiceCode("=HYPERLINK(\"http://evil\")")
                .tenantName("Nguyễn Văn A")
                .roomNumber("P.301")
                .bedCode("G1")
                .amount(new BigDecimal("1500000"))
                .paymentMethod(PaymentMethod.CASH)
                .confirmedAt(Instant.parse("2026-10-08T03:00:00Z"))
                .build(),
            FinancialReportPaidInvoiceItem.builder()
                .invoiceId(UUID.randomUUID())
                .invoiceCode("INV-202610-002")
                .tenantName("Lê Thị Bình")
                .roomNumber("P.302")
                .bedCode("G2")
                .amount(new BigDecimal("1500000"))
                .paymentMethod(PaymentMethod.VIETQR_TRANSFER)
                .confirmedAt(Instant.parse("2026-10-07T03:00:00Z"))
                .build());

    List<FinancialReportMaintenanceTicketItem> tickets =
        List.of(
            FinancialReportMaintenanceTicketItem.builder()
                .ticketId(UUID.randomUUID())
                .ticketCode("MT-2026-001")
                .title("+cmd|'/C calc'!A0")
                .roomNumber("P.301")
                .resolvedAt(Instant.parse("2026-10-15T04:00:00Z"))
                .partsCost(new BigDecimal("500000"))
                .build(),
            FinancialReportMaintenanceTicketItem.builder()
                .ticketId(UUID.randomUUID())
                .ticketCode("MT-2026-002")
                .title("Sửa ống nước tầng 3")
                .roomNumber("P.303")
                .resolvedAt(Instant.parse("2026-10-11T04:00:00Z"))
                .partsCost(BigDecimal.ZERO)
                .build());

    return EstimatedNetProfitReportResponse.builder()
        .period("2026-10")
        .periodStartDate(LocalDate.of(2026, 10, 1))
        .periodEndDate(LocalDate.of(2026, 10, 31))
        .label(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_LABEL)
        .disclaimer(FinancialReportServiceImpl.ESTIMATED_NET_PROFIT_DISCLAIMER)
        .expectedRentRevenue(new BigDecimal("6000000"))
        .collectedRentRevenue(new BigDecimal("3000000"))
        .maintenancePartsCost(new BigDecimal("500000"))
        .facilityExpenseTotal(new BigDecimal("5500000"))
        .totalExpenses(new BigDecimal("6000000"))
        .estimatedNetProfit(new BigDecimal("-3000000"))
        .expenseBreakdownByType(breakdown)
        .totalInvoiceCount(4)
        .paidInvoiceCount(2)
        .completedTicketCount(2)
        .facilityExpenseCount(3)
        .paidInvoices(paidInvoices)
        .completedMaintenanceTickets(tickets)
        .facilityExpenses(expenses)
        .build();
  }
}
