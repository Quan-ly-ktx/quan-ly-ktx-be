package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.configs.TimeConfig;
import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseResponse;
import com.uit.dormmanagement.dtos.response.finance.EstimatedNetProfitReportResponse;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportMaintenanceTicketItem;
import com.uit.dormmanagement.dtos.response.finance.FinancialReportPaidInvoiceItem;
import com.uit.dormmanagement.entities.Contract;
import com.uit.dormmanagement.entities.FacilityExpense;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.entities.MaintenanceTicket;
import com.uit.dormmanagement.entities.PaymentTransaction;
import com.uit.dormmanagement.entities.TicketReplacementPart;
import com.uit.dormmanagement.enums.ExpenseType;
import com.uit.dormmanagement.enums.PaymentMethod;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ExpenseErrorCode;
import com.uit.dormmanagement.repositories.FacilityExpenseRepository;
import com.uit.dormmanagement.repositories.InvoiceRepository;
import com.uit.dormmanagement.repositories.MaintenanceTicketRepository;
import com.uit.dormmanagement.services.FinancialReportService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinancialReportServiceImpl implements FinancialReportService {

  public static final String ESTIMATED_NET_PROFIT_LABEL = "Lợi nhuận ròng (ước tính)";
  public static final String ESTIMATED_NET_PROFIT_DISCLAIMER =
      "Ước tính dựa trên các khoản thu và chi đã được ghi nhận; có thể chưa bao gồm đầy đủ chi phí, thuế, khấu hao và nghĩa vụ khác.";
  private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

  private final Clock clock;
  private final InvoiceRepository invoiceRepository;
  private final MaintenanceTicketRepository maintenanceTicketRepository;
  private final FacilityExpenseRepository facilityExpenseRepository;

  @Override
  @Transactional(readOnly = true)
  public EstimatedNetProfitReportResponse getEstimatedNetProfitReport(String period) {
    YearMonth yearMonth = resolveYearMonth(period);
    String formattedPeriod = yearMonth.toString();

    LocalDate periodStartDate = yearMonth.atDay(1);
    LocalDate periodEndDate = yearMonth.atEndOfMonth();
    Instant periodStartInstant = periodStartDate.atStartOfDay(TimeConfig.DORM_ZONE).toInstant();
    Instant periodEndExclusiveInstant =
        yearMonth.plusMonths(1).atDay(1).atStartOfDay(TimeConfig.DORM_ZONE).toInstant();

    List<InvoiceRepository.InvoiceSummaryProjection> summaryInvoices =
        invoiceRepository.findAllForSummary(periodStartDate);
    int totalInvoiceCount = summaryInvoices.size();
    BigDecimal expectedRentRevenue =
        summaryInvoices.stream()
            .map(InvoiceRepository.InvoiceSummaryProjection::getAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    List<Invoice> paidEntityList =
        invoiceRepository.findPaidInvoicesForPeriodWithDetails(periodStartDate);
    int paidInvoiceCount = paidEntityList.size();
    BigDecimal collectedRentRevenue =
        paidEntityList.stream()
            .map(Invoice::getAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    List<FinancialReportPaidInvoiceItem> paidInvoices =
        paidEntityList.stream()
            .map(FinancialReportServiceImpl::toPaidInvoiceItem)
            .sorted(
                Comparator.comparing(
                        FinancialReportPaidInvoiceItem::confirmedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(
                        FinancialReportPaidInvoiceItem::invoiceCode,
                        Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

    List<MaintenanceTicket> tickets =
        maintenanceTicketRepository.findCompletedTicketsBetweenWithParts(
            periodStartInstant, periodEndExclusiveInstant);
    int completedTicketCount = tickets.size();
    List<FinancialReportMaintenanceTicketItem> completedMaintenanceTickets =
        tickets.stream()
            .map(FinancialReportServiceImpl::toMaintenanceTicketItem)
            .sorted(
                Comparator.comparing(
                        FinancialReportMaintenanceTicketItem::resolvedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(
                        FinancialReportMaintenanceTicketItem::ticketCode,
                        Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    BigDecimal maintenancePartsCost =
        completedMaintenanceTickets.stream()
            .map(FinancialReportMaintenanceTicketItem::partsCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    List<FacilityExpense> expenses =
        facilityExpenseRepository.findByExpenseDateBetweenOrderByExpenseDateDescCreatedAtDesc(
            periodStartDate, periodEndDate);
    int facilityExpenseCount = expenses.size();

    Map<ExpenseType, BigDecimal> expenseBreakdownByType = new LinkedHashMap<>();
    for (ExpenseType expenseType : ExpenseType.values()) {
      expenseBreakdownByType.put(expenseType, BigDecimal.ZERO);
    }
    for (FacilityExpense expense : expenses) {
      if (expense.getExpenseType() != null && expense.getAmount() != null) {
        expenseBreakdownByType.merge(
            expense.getExpenseType(), expense.getAmount(), BigDecimal::add);
      }
    }

    BigDecimal facilityExpenseTotal =
        expenses.stream()
            .map(FacilityExpense::getAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    List<FacilityExpenseResponse> facilityExpenseResponses =
        expenses.stream().map(FacilityExpenseServiceImpl::toResponse).toList();

    BigDecimal totalExpenses = maintenancePartsCost.add(facilityExpenseTotal);
    BigDecimal estimatedNetProfit = collectedRentRevenue.subtract(totalExpenses);

    return EstimatedNetProfitReportResponse.builder()
        .period(formattedPeriod)
        .periodStartDate(periodStartDate)
        .periodEndDate(periodEndDate)
        .label(ESTIMATED_NET_PROFIT_LABEL)
        .disclaimer(ESTIMATED_NET_PROFIT_DISCLAIMER)
        .expectedRentRevenue(expectedRentRevenue)
        .collectedRentRevenue(collectedRentRevenue)
        .maintenancePartsCost(maintenancePartsCost)
        .facilityExpenseTotal(facilityExpenseTotal)
        .totalExpenses(totalExpenses)
        .estimatedNetProfit(estimatedNetProfit)
        .expenseBreakdownByType(Collections.unmodifiableMap(expenseBreakdownByType))
        .totalInvoiceCount(totalInvoiceCount)
        .paidInvoiceCount(paidInvoiceCount)
        .completedTicketCount(completedTicketCount)
        .facilityExpenseCount(facilityExpenseCount)
        .paidInvoices(paidInvoices)
        .completedMaintenanceTickets(completedMaintenanceTickets)
        .facilityExpenses(facilityExpenseResponses)
        .build();
  }

  private YearMonth resolveYearMonth(String period) {
    if (period == null || period.isBlank()) {
      return YearMonth.now(clock.withZone(TimeConfig.DORM_ZONE));
    }
    String trimmedPeriod = period.trim();
    if (!PERIOD_PATTERN.matcher(trimmedPeriod).matches()) {
      throw new BaseAppException(ExpenseErrorCode.INVALID_REPORT_PERIOD);
    }
    return YearMonth.parse(trimmedPeriod);
  }

  private static FinancialReportPaidInvoiceItem toPaidInvoiceItem(Invoice invoice) {
    Contract contract = invoice.getContract();
    String tenantName =
        contract != null && contract.getTenant() != null
            ? contract.getTenant().getFullName()
            : null;
    String roomNumber = resolveRoomNumber(contract);
    String bedCode = resolveBedCode(contract);

    PaymentTransaction paymentTransaction = invoice.getPaymentTransaction();
    PaymentMethod paymentMethod =
        paymentTransaction != null ? paymentTransaction.getPaymentMethod() : null;
    Instant confirmedAt = paymentTransaction != null ? paymentTransaction.getConfirmedAt() : null;

    return FinancialReportPaidInvoiceItem.builder()
        .invoiceId(invoice.getId())
        .invoiceCode(invoice.getInvoiceCode())
        .tenantName(tenantName)
        .roomNumber(roomNumber)
        .bedCode(bedCode)
        .amount(invoice.getAmount())
        .paymentMethod(paymentMethod)
        .confirmedAt(confirmedAt)
        .build();
  }

  private static String resolveRoomNumber(Contract contract) {
    if (contract == null) {
      return null;
    }
    if (contract.getRoomCodeSnapshot() != null && !contract.getRoomCodeSnapshot().isBlank()) {
      return contract.getRoomCodeSnapshot();
    }
    if (contract.getBed() != null && contract.getBed().getRoom() != null) {
      return contract.getBed().getRoom().getRoomNumber();
    }
    return null;
  }

  private static String resolveBedCode(Contract contract) {
    if (contract == null) {
      return null;
    }
    if (contract.getBedCodeSnapshot() != null && !contract.getBedCodeSnapshot().isBlank()) {
      return contract.getBedCodeSnapshot();
    }
    if (contract.getBed() != null) {
      return contract.getBed().getBedCode();
    }
    return null;
  }

  private static FinancialReportMaintenanceTicketItem toMaintenanceTicketItem(
      MaintenanceTicket ticket) {
    List<TicketReplacementPart> parts =
        ticket.getReplacementParts() != null ? ticket.getReplacementParts() : List.of();
    BigDecimal partsCost =
        parts.stream()
            .map(
                part ->
                    BigDecimal.valueOf(part.getQuantity())
                        .multiply(
                            part.getUnitCost() != null ? part.getUnitCost() : BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    String roomNumber = ticket.getRoom() != null ? ticket.getRoom().getRoomNumber() : null;

    return FinancialReportMaintenanceTicketItem.builder()
        .ticketId(ticket.getId())
        .ticketCode(ticket.getTicketCode())
        .title(ticket.getTitle())
        .roomNumber(roomNumber)
        .resolvedAt(ticket.getResolvedAt())
        .partsCost(partsCost)
        .build();
  }
}
