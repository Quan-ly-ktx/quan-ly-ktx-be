package com.uit.dormmanagement.services.impl;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseResponse;
import com.uit.dormmanagement.dtos.response.finance.EstimatedNetProfitReportResponse;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportExportFile;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportMaintenanceTicketItem;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportPaidInvoiceItem;
import com.uit.dormmanagement.enums.ExpenseType;
import com.uit.dormmanagement.enums.FinancialReportExportFormat;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ExpenseErrorCode;
import com.uit.dormmanagement.exceptions.error.SystemErrorCode;
import com.uit.dormmanagement.services.FinancialReportExportService;
import com.uit.dormmanagement.services.FinancialReportService;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinancialReportExportServiceImpl implements FinancialReportExportService {

  public static final String XLSX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  public static final String PDF_CONTENT_TYPE = "application/pdf";

  private static final String FONT_CLASSPATH = "fonts/NotoSans-Regular.ttf";
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(TimeConfig.DORM_ZONE);

  private static volatile byte[] cachedFontBytes;

  private final FinancialReportService financialReportService;

  @Override
  @Transactional(readOnly = true)
  public FinancialReportExportFile exportEstimatedNetProfitReport(String period, String format) {
    FinancialReportExportFormat exportFormat = parseFormat(format);
    EstimatedNetProfitReportResponse report =
        financialReportService.getEstimatedNetProfitReport(period);

    return switch (exportFormat) {
      case XLSX -> buildXlsxExport(report);
      case PDF -> buildPdfExport(report);
    };
  }

  public static String sanitizeSpreadsheetText(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    char first = text.charAt(0);
    if (first == '='
        || first == '+'
        || first == '-'
        || first == '@'
        || first == '\t'
        || first == '\r') {
      return "'" + text;
    }
    return text;
  }

  private static FinancialReportExportFormat parseFormat(String format) {
    if (format == null || format.isBlank()) {
      throw new BaseAppException(ExpenseErrorCode.INVALID_EXPORT_FORMAT);
    }
    try {
      return FinancialReportExportFormat.valueOf(format.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BaseAppException(ExpenseErrorCode.INVALID_EXPORT_FORMAT);
    }
  }

  private FinancialReportExportFile buildXlsxExport(EstimatedNetProfitReportResponse report) {
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XlsxStyles styles = createXlsxStyles(workbook);

      writeSummarySheet(workbook, report, styles);
      writeFacilityExpensesSheet(workbook, report.facilityExpenses(), styles);
      writePaidInvoicesSheet(workbook, report.paidInvoices(), styles);
      writeMaintenanceTicketsSheet(workbook, report.completedMaintenanceTickets(), styles);

      workbook.write(out);
      return FinancialReportExportFile.builder()
          .content(out.toByteArray())
          .contentType(XLSX_CONTENT_TYPE)
          .filename("thu-chi-" + report.period() + ".xlsx")
          .build();
    } catch (IOException e) {
      throw new BaseAppException(
          SystemErrorCode.INTERNAL_SERVER_ERROR, "Không thể xuất tệp Excel báo cáo tài chính");
    }
  }

  private void writeSummarySheet(
      Workbook workbook, EstimatedNetProfitReportResponse report, XlsxStyles styles) {
    Sheet sheet = workbook.createSheet("Tong hop thu chi");
    setColumnWidths(sheet, 44, 24, 20, 22);

    Row titleRow = sheet.createRow(0);
    setTextCell(titleRow, 0, "BÁO CÁO THU CHI & LỢI NHUẬN RÒNG (ƯỚC TÍNH)", styles.title());

    Row periodRow = sheet.createRow(1);
    setTextCell(periodRow, 0, "Kỳ báo cáo", styles.boldText());
    setTextCell(periodRow, 1, report.period(), styles.text());

    Row dateRangeRow = sheet.createRow(2);
    setTextCell(dateRangeRow, 0, "Từ ngày", styles.boldText());
    setTextCell(dateRangeRow, 1, formatDate(report.periodStartDate()), styles.text());
    setTextCell(dateRangeRow, 2, "Đến ngày", styles.boldText());
    setTextCell(dateRangeRow, 3, formatDate(report.periodEndDate()), styles.text());

    Row kpiSectionRow = sheet.createRow(4);
    setTextCell(kpiSectionRow, 0, "CHỈ SỐ TỔNG HỢP", styles.boldText());

    Row kpiHeaderRow = sheet.createRow(5);
    writeHeaderRow(kpiHeaderRow, styles.header(), "Chỉ tiêu", "Giá trị (VNĐ)", "Số lượng");

    writeKpiRow(
        sheet.createRow(6),
        "Doanh thu dự kiến (tiền phòng)",
        report.expectedRentRevenue(),
        report.totalInvoiceCount(),
        styles);
    writeKpiRow(
        sheet.createRow(7),
        "Doanh thu thực thu (tiền phòng đã thu)",
        report.collectedRentRevenue(),
        report.paidInvoiceCount(),
        styles);
    writeKpiRow(
        sheet.createRow(8),
        "Chi phí vật tư sửa chữa",
        report.maintenancePartsCost(),
        report.completedTicketCount(),
        styles);
    writeKpiRow(
        sheet.createRow(9),
        "Chi phí vận hành cơ sở",
        report.facilityExpenseTotal(),
        report.facilityExpenseCount(),
        styles);
    writeKpiRow(sheet.createRow(10), "Tổng chi phí", report.totalExpenses(), null, styles);
    writeKpiRow(
        sheet.createRow(11), report.label(), report.estimatedNetProfit(), null, styles, true);

    Row breakdownSectionRow = sheet.createRow(13);
    setTextCell(breakdownSectionRow, 0, "PHÂN BỔ CHI PHÍ VẬN HÀNH THEO LOẠI", styles.boldText());

    Row breakdownHeaderRow = sheet.createRow(14);
    writeHeaderRow(breakdownHeaderRow, styles.header(), "Loại chi phí", "Mã loại", "Số tiền (VNĐ)");

    Map<ExpenseType, BigDecimal> breakdown =
        report.expenseBreakdownByType() != null ? report.expenseBreakdownByType() : Map.of();
    int rowIdx = 15;
    for (ExpenseType expenseType : ExpenseType.values()) {
      Row row = sheet.createRow(rowIdx++);
      setTextCell(row, 0, expenseType.getDisplayName(), styles.text());
      setTextCell(row, 1, expenseType.name(), styles.text());
      setNumericCurrencyCell(
          row, 2, breakdown.getOrDefault(expenseType, BigDecimal.ZERO), styles.currency());
    }

    Row disclaimerRow = sheet.createRow(rowIdx + 1);
    setTextCell(disclaimerRow, 0, "Lưu ý", styles.boldText());
    setTextCell(disclaimerRow, 1, report.disclaimer(), styles.text());
  }

  private void writeFacilityExpensesSheet(
      Workbook workbook, List<FacilityExpenseResponse> expenses, XlsxStyles styles) {
    Sheet sheet = workbook.createSheet("Chi tiet chi phi");
    setColumnWidths(sheet, 16, 24, 34, 20, 20, 24, 36);

    Row headerRow = sheet.createRow(0);
    writeHeaderRow(
        headerRow,
        styles.header(),
        "Ngày chi phí",
        "Loại chi phí",
        "Tiêu đề",
        "Mã chứng từ",
        "Số tiền (VNĐ)",
        "Người ghi nhận",
        "Ghi chú");

    List<FacilityExpenseResponse> items = expenses != null ? expenses : List.of();
    int rowIdx = 1;
    for (FacilityExpenseResponse item : items) {
      Row row = sheet.createRow(rowIdx++);
      setTextCell(row, 0, formatDate(item.expenseDate()), styles.text());
      setTextCell(
          row,
          1,
          item.expenseType() != null ? item.expenseType().getDisplayName() : "",
          styles.text());
      setTextCell(row, 2, item.title(), styles.text());
      setTextCell(row, 3, item.invoiceCode(), styles.text());
      setNumericCurrencyCell(row, 4, item.amount(), styles.currency());
      setTextCell(row, 5, item.recordedByStaffName(), styles.text());
      setTextCell(row, 6, item.notes(), styles.text());
    }
  }

  private void writePaidInvoicesSheet(
      Workbook workbook, List<FinancialReportPaidInvoiceItem> paidInvoices, XlsxStyles styles) {
    Sheet sheet = workbook.createSheet("Hoa don da thu");
    setColumnWidths(sheet, 22, 26, 14, 14, 24, 22, 22);

    Row headerRow = sheet.createRow(0);
    writeHeaderRow(
        headerRow,
        styles.header(),
        "Mã hóa đơn",
        "Khách thuê",
        "Phòng",
        "Giường",
        "Phương thức",
        "Số tiền đã thu (VNĐ)",
        "Thời điểm xác nhận");

    List<FinancialReportPaidInvoiceItem> items = paidInvoices != null ? paidInvoices : List.of();
    int rowIdx = 1;
    for (FinancialReportPaidInvoiceItem item : items) {
      Row row = sheet.createRow(rowIdx++);
      setTextCell(row, 0, item.invoiceCode(), styles.text());
      setTextCell(row, 1, item.tenantName(), styles.text());
      setTextCell(row, 2, item.roomNumber(), styles.text());
      setTextCell(row, 3, item.bedCode(), styles.text());
      setTextCell(
          row,
          4,
          item.paymentMethod() != null ? item.paymentMethod().getDisplayName() : "",
          styles.text());
      setNumericCurrencyCell(row, 5, item.amount(), styles.currency());
      setTextCell(row, 6, formatInstant(item.confirmedAt()), styles.text());
    }
  }

  private void writeMaintenanceTicketsSheet(
      Workbook workbook, List<FinancialReportMaintenanceTicketItem> tickets, XlsxStyles styles) {
    Sheet sheet = workbook.createSheet("Sua chua hoan thanh");
    setColumnWidths(sheet, 20, 36, 14, 22, 22);

    Row headerRow = sheet.createRow(0);
    writeHeaderRow(
        headerRow,
        styles.header(),
        "Mã phiếu",
        "Tiêu đề",
        "Phòng",
        "Thời điểm hoàn thành",
        "Chi phí vật tư (VNĐ)");

    List<FinancialReportMaintenanceTicketItem> items = tickets != null ? tickets : List.of();
    int rowIdx = 1;
    for (FinancialReportMaintenanceTicketItem item : items) {
      Row row = sheet.createRow(rowIdx++);
      setTextCell(row, 0, item.ticketCode(), styles.text());
      setTextCell(row, 1, item.title(), styles.text());
      setTextCell(row, 2, item.roomNumber(), styles.text());
      setTextCell(row, 3, formatInstant(item.resolvedAt()), styles.text());
      setNumericCurrencyCell(row, 4, item.partsCost(), styles.currency());
    }
  }

  private static void writeKpiRow(
      Row row, String label, BigDecimal amount, Integer count, XlsxStyles styles) {
    writeKpiRow(row, label, amount, count, styles, false);
  }

  private static void writeKpiRow(
      Row row,
      String label,
      BigDecimal amount,
      Integer count,
      XlsxStyles styles,
      boolean highlight) {
    setTextCell(row, 0, label, highlight ? styles.boldText() : styles.text());
    setNumericCurrencyCell(row, 1, amount, highlight ? styles.boldCurrency() : styles.currency());
    if (count != null) {
      Cell countCell = row.createCell(2);
      countCell.setCellValue(count.doubleValue());
      countCell.setCellStyle(styles.integer());
    } else {
      setTextCell(row, 2, "", styles.text());
    }
  }

  private static void writeHeaderRow(Row row, CellStyle headerStyle, String... headers) {
    for (int i = 0; i < headers.length; i++) {
      setTextCell(row, i, headers[i], headerStyle);
    }
  }

  private static void setTextCell(Row row, int columnIndex, String value, CellStyle style) {
    String sanitized = value == null ? "" : sanitizeSpreadsheetText(value);
    Cell cell = row.createCell(columnIndex);
    cell.setCellValue(sanitized);
    if (style != null) {
      cell.setCellStyle(style);
    }
  }

  private static void setNumericCurrencyCell(
      Row row, int columnIndex, BigDecimal value, CellStyle style) {
    BigDecimal effective = value != null ? value : BigDecimal.ZERO;
    Cell cell = row.createCell(columnIndex);
    cell.setCellValue(effective.doubleValue());
    if (style != null) {
      cell.setCellStyle(style);
    }
  }

  private static void setColumnWidths(Sheet sheet, int... widthsInChars) {
    for (int i = 0; i < widthsInChars.length; i++) {
      sheet.setColumnWidth(i, widthsInChars[i] * 256);
    }
  }

  private static XlsxStyles createXlsxStyles(Workbook workbook) {
    org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
    titleFont.setBold(true);
    titleFont.setFontHeightInPoints((short) 14);
    CellStyle titleStyle = workbook.createCellStyle();
    titleStyle.setFont(titleFont);

    org.apache.poi.ss.usermodel.Font boldFont = workbook.createFont();
    boldFont.setBold(true);

    CellStyle headerStyle = workbook.createCellStyle();
    headerStyle.setFont(boldFont);
    headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
    headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    applyThinBorders(headerStyle);

    CellStyle textStyle = workbook.createCellStyle();
    applyThinBorders(textStyle);

    CellStyle boldTextStyle = workbook.createCellStyle();
    boldTextStyle.setFont(boldFont);
    applyThinBorders(boldTextStyle);

    short currencyFormat = workbook.createDataFormat().getFormat("#,##0");

    CellStyle currencyStyle = workbook.createCellStyle();
    currencyStyle.setDataFormat(currencyFormat);
    currencyStyle.setAlignment(HorizontalAlignment.RIGHT);
    applyThinBorders(currencyStyle);

    CellStyle boldCurrencyStyle = workbook.createCellStyle();
    boldCurrencyStyle.setFont(boldFont);
    boldCurrencyStyle.setDataFormat(currencyFormat);
    boldCurrencyStyle.setAlignment(HorizontalAlignment.RIGHT);
    applyThinBorders(boldCurrencyStyle);

    CellStyle integerStyle = workbook.createCellStyle();
    integerStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
    integerStyle.setAlignment(HorizontalAlignment.RIGHT);
    applyThinBorders(integerStyle);

    return new XlsxStyles(
        titleStyle,
        headerStyle,
        textStyle,
        boldTextStyle,
        currencyStyle,
        boldCurrencyStyle,
        integerStyle);
  }

  private static void applyThinBorders(CellStyle style) {
    style.setBorderTop(BorderStyle.THIN);
    style.setBorderBottom(BorderStyle.THIN);
    style.setBorderLeft(BorderStyle.THIN);
    style.setBorderRight(BorderStyle.THIN);
  }

  private record XlsxStyles(
      CellStyle title,
      CellStyle header,
      CellStyle text,
      CellStyle boldText,
      CellStyle currency,
      CellStyle boldCurrency,
      CellStyle integer) {}

  private FinancialReportExportFile buildPdfExport(EstimatedNetProfitReportResponse report) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] fontBytes = loadFontBytes();
      BaseFont baseFont =
          BaseFont.createFont(
              FONT_CLASSPATH, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, fontBytes, null);

      Font titleFont = new Font(baseFont, 15f, Font.BOLD);
      Font sectionFont = new Font(baseFont, 11f, Font.BOLD);
      Font headerFont = new Font(baseFont, 9f, Font.BOLD);
      Font bodyFont = new Font(baseFont, 9f, Font.NORMAL);
      Font boldBodyFont = new Font(baseFont, 9f, Font.BOLD);
      Font disclaimerFont = new Font(baseFont, 8.5f, Font.ITALIC, new Color(75, 85, 99));

      Document document = new Document(PageSize.A4, 36f, 36f, 36f, 36f);
      PdfWriter.getInstance(document, out);
      document.open();

      Paragraph title =
          new Paragraph(nfc("BÁO CÁO THU CHI & LỢI NHUẬN RÒNG (ƯỚC TÍNH)"), titleFont);
      title.setSpacingAfter(4f);
      document.add(title);

      Paragraph periodPara =
          new Paragraph(
              nfc(
                  "Kỳ báo cáo: "
                      + report.period()
                      + " ("
                      + formatDate(report.periodStartDate())
                      + " - "
                      + formatDate(report.periodEndDate())
                      + ")"),
              bodyFont);
      periodPara.setSpacingAfter(10f);
      document.add(periodPara);

      addPdfSectionTitle(document, "1. Tổng hợp thu chi", sectionFont);
      PdfPTable kpiTable = createTable(new float[] {5f, 3f, 2f});
      addPdfHeaderRow(kpiTable, headerFont, "Chỉ tiêu", "Giá trị (VNĐ)", "Số lượng");
      addPdfRow(
          kpiTable,
          bodyFont,
          new int[] {Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT},
          "Doanh thu dự kiến (tiền phòng)",
          formatVnd(report.expectedRentRevenue()),
          String.valueOf(report.totalInvoiceCount()));
      addPdfRow(
          kpiTable,
          bodyFont,
          new int[] {Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT},
          "Doanh thu thực thu (tiền phòng đã thu)",
          formatVnd(report.collectedRentRevenue()),
          String.valueOf(report.paidInvoiceCount()));
      addPdfRow(
          kpiTable,
          bodyFont,
          new int[] {Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT},
          "Chi phí vật tư sửa chữa",
          formatVnd(report.maintenancePartsCost()),
          String.valueOf(report.completedTicketCount()));
      addPdfRow(
          kpiTable,
          bodyFont,
          new int[] {Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT},
          "Chi phí vận hành cơ sở",
          formatVnd(report.facilityExpenseTotal()),
          String.valueOf(report.facilityExpenseCount()));
      addPdfRow(
          kpiTable,
          bodyFont,
          new int[] {Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT},
          "Tổng chi phí",
          formatVnd(report.totalExpenses()),
          "-");
      addPdfRow(
          kpiTable,
          boldBodyFont,
          new int[] {Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT},
          report.label(),
          formatVnd(report.estimatedNetProfit()),
          "-");
      document.add(kpiTable);

      Paragraph disclaimerPara =
          new Paragraph(nfc("Lưu ý: " + report.disclaimer()), disclaimerFont);
      disclaimerPara.setSpacingBefore(6f);
      disclaimerPara.setSpacingAfter(10f);
      document.add(disclaimerPara);

      addPdfSectionTitle(document, "2. Phân bổ chi phí vận hành theo loại", sectionFont);
      PdfPTable breakdownTable = createTable(new float[] {4.5f, 2.5f, 3f});
      addPdfHeaderRow(breakdownTable, headerFont, "Loại chi phí", "Mã loại", "Số tiền (VNĐ)");
      Map<ExpenseType, BigDecimal> breakdown =
          report.expenseBreakdownByType() != null ? report.expenseBreakdownByType() : Map.of();
      for (ExpenseType expenseType : ExpenseType.values()) {
        addPdfRow(
            breakdownTable,
            bodyFont,
            new int[] {Element.ALIGN_LEFT, Element.ALIGN_LEFT, Element.ALIGN_RIGHT},
            expenseType.getDisplayName(),
            expenseType.name(),
            formatVnd(breakdown.getOrDefault(expenseType, BigDecimal.ZERO)));
      }
      document.add(breakdownTable);

      addPdfSectionTitle(document, "3. Chi tiết chi phí vận hành", sectionFont);
      PdfPTable expenseTable = createTable(new float[] {1.8f, 2.2f, 3.2f, 1.8f, 2f});
      addPdfHeaderRow(
          expenseTable, headerFont, "Ngày", "Loại chi phí", "Tiêu đề", "Chứng từ", "Số tiền");
      List<FacilityExpenseResponse> expenses =
          report.facilityExpenses() != null ? report.facilityExpenses() : List.of();
      if (expenses.isEmpty()) {
        addPdfEmptyRow(expenseTable, bodyFont, 5);
      } else {
        for (FacilityExpenseResponse expense : expenses) {
          addPdfRow(
              expenseTable,
              bodyFont,
              new int[] {
                Element.ALIGN_LEFT,
                Element.ALIGN_LEFT,
                Element.ALIGN_LEFT,
                Element.ALIGN_LEFT,
                Element.ALIGN_RIGHT
              },
              formatDate(expense.expenseDate()),
              expense.expenseType() != null ? expense.expenseType().getDisplayName() : "",
              expense.title(),
              expense.invoiceCode(),
              formatVnd(expense.amount()));
        }
      }
      document.add(expenseTable);

      addPdfSectionTitle(document, "4. Hóa đơn tiền phòng đã thu", sectionFont);
      PdfPTable invoiceTable = createTable(new float[] {2.4f, 2.6f, 1.8f, 2f, 2f});
      addPdfHeaderRow(
          invoiceTable,
          headerFont,
          "Mã hóa đơn",
          "Khách thuê",
          "Phòng/Giường",
          "Phương thức",
          "Số tiền");
      List<FinancialReportPaidInvoiceItem> paidInvoices =
          report.paidInvoices() != null ? report.paidInvoices() : List.of();
      if (paidInvoices.isEmpty()) {
        addPdfEmptyRow(invoiceTable, bodyFont, 5);
      } else {
        for (FinancialReportPaidInvoiceItem item : paidInvoices) {
          String roomBed =
              (item.roomNumber() != null ? item.roomNumber() : "")
                  + (item.bedCode() != null ? " / " + item.bedCode() : "");
          addPdfRow(
              invoiceTable,
              bodyFont,
              new int[] {
                Element.ALIGN_LEFT,
                Element.ALIGN_LEFT,
                Element.ALIGN_LEFT,
                Element.ALIGN_LEFT,
                Element.ALIGN_RIGHT
              },
              item.invoiceCode(),
              item.tenantName(),
              roomBed.trim(),
              item.paymentMethod() != null ? item.paymentMethod().getDisplayName() : "",
              formatVnd(item.amount()));
        }
      }
      document.add(invoiceTable);

      addPdfSectionTitle(document, "5. Phiếu sửa chữa đã hoàn thành", sectionFont);
      PdfPTable ticketTable = createTable(new float[] {2f, 3.5f, 1.5f, 2.2f, 2f});
      addPdfHeaderRow(
          ticketTable, headerFont, "Mã phiếu", "Tiêu đề", "Phòng", "Hoàn thành", "Chi phí vật tư");
      List<FinancialReportMaintenanceTicketItem> tickets =
          report.completedMaintenanceTickets() != null
              ? report.completedMaintenanceTickets()
              : List.of();
      if (tickets.isEmpty()) {
        addPdfEmptyRow(ticketTable, bodyFont, 5);
      } else {
        for (FinancialReportMaintenanceTicketItem item : tickets) {
          addPdfRow(
              ticketTable,
              bodyFont,
              new int[] {
                Element.ALIGN_LEFT,
                Element.ALIGN_LEFT,
                Element.ALIGN_LEFT,
                Element.ALIGN_LEFT,
                Element.ALIGN_RIGHT
              },
              item.ticketCode(),
              item.title(),
              item.roomNumber(),
              formatInstant(item.resolvedAt()),
              formatVnd(item.partsCost()));
        }
      }
      document.add(ticketTable);

      document.close();
      return FinancialReportExportFile.builder()
          .content(out.toByteArray())
          .contentType(PDF_CONTENT_TYPE)
          .filename("thu-chi-" + report.period() + ".pdf")
          .build();
    } catch (IOException | DocumentException e) {
      throw new BaseAppException(
          SystemErrorCode.INTERNAL_SERVER_ERROR, "Không thể xuất tệp PDF báo cáo tài chính");
    }
  }

  private static byte[] loadFontBytes() throws IOException {
    byte[] existing = cachedFontBytes;
    if (existing != null) {
      return existing;
    }
    synchronized (FinancialReportExportServiceImpl.class) {
      if (cachedFontBytes == null) {
        ClassPathResource resource = new ClassPathResource(FONT_CLASSPATH);
        try (InputStream is = resource.getInputStream()) {
          cachedFontBytes = is.readAllBytes();
        }
      }
      return cachedFontBytes;
    }
  }

  private static void addPdfSectionTitle(Document document, String text, Font font)
      throws DocumentException {
    Paragraph section = new Paragraph(nfc(text), font);
    section.setSpacingBefore(10f);
    section.setSpacingAfter(4f);
    document.add(section);
  }

  private static PdfPTable createTable(float[] widths) {
    PdfPTable table = new PdfPTable(widths);
    table.setWidthPercentage(100f);
    table.setHeaderRows(1);
    return table;
  }

  private static void addPdfHeaderRow(PdfPTable table, Font font, String... headers) {
    for (String header : headers) {
      PdfPCell cell = new PdfPCell(new Phrase(nfc(header), font));
      cell.setBackgroundColor(new Color(243, 244, 246));
      cell.setPadding(5f);
      cell.setHorizontalAlignment(Element.ALIGN_LEFT);
      table.addCell(cell);
    }
  }

  private static void addPdfRow(PdfPTable table, Font font, int[] alignments, String... values) {
    for (int i = 0; i < values.length; i++) {
      PdfPCell cell = new PdfPCell(new Phrase(nfc(values[i]), font));
      cell.setPadding(4.5f);
      cell.setHorizontalAlignment(alignments[i]);
      table.addCell(cell);
    }
  }

  private static void addPdfEmptyRow(PdfPTable table, Font font, int colspan) {
    PdfPCell cell = new PdfPCell(new Phrase(nfc("Không có dữ liệu trong kỳ."), font));
    cell.setColspan(colspan);
    cell.setPadding(5f);
    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
    table.addCell(cell);
  }

  private static String nfc(String text) {
    return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFC);
  }

  private static String formatDate(LocalDate date) {
    return date != null ? DATE_FORMATTER.format(date) : "";
  }

  private static String formatInstant(Instant instant) {
    return instant != null ? DATE_TIME_FORMATTER.format(instant) : "";
  }

  private static String formatVnd(BigDecimal amount) {
    BigDecimal effective = amount != null ? amount : BigDecimal.ZERO;
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
    symbols.setGroupingSeparator('.');
    DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
    return formatter.format(effective) + " đ";
  }
}
