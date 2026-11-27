package com.uit.dormmanagement.dtos.request.invoice;

import java.time.LocalDate;
import lombok.Builder;

@Builder
public record IssueInvoiceBatchRequest(LocalDate billingPeriod) {}
