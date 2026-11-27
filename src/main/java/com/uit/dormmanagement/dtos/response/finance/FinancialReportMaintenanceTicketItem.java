package com.uit.dormmanagement.dtos.response.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record FinancialReportMaintenanceTicketItem(
    UUID ticketId,
    String ticketCode,
    String title,
    String roomNumber,
    Instant resolvedAt,
    BigDecimal partsCost) {}
