package com.uit.dormmanagement.dtos.response.finance;

import com.uit.dormmanagement.dtos.response.expense.FacilityExpenseResponse;
import com.uit.dormmanagement.enums.ExpenseType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;

@Builder
public record EstimatedNetProfitReportResponse(
    String period,
    LocalDate periodStartDate,
    LocalDate periodEndDate,
    String label,
    String disclaimer,
    BigDecimal expectedRentRevenue,
    BigDecimal collectedRentRevenue,
    BigDecimal maintenancePartsCost,
    BigDecimal facilityExpenseTotal,
    BigDecimal totalExpenses,
    BigDecimal estimatedNetProfit,
    Map<ExpenseType, BigDecimal> expenseBreakdownByType,
    int totalInvoiceCount,
    int paidInvoiceCount,
    int completedTicketCount,
    int facilityExpenseCount,
    List<FinancialReportPaidInvoiceItem> paidInvoices,
    List<FinancialReportMaintenanceTicketItem> completedMaintenanceTickets,
    List<FacilityExpenseResponse> facilityExpenses) {}
