package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.response.finance.EstimatedNetProfitReportResponse;

public interface FinancialReportService {

  EstimatedNetProfitReportResponse getEstimatedNetProfitReport(String period);
}
