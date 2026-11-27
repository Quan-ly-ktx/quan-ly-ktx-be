package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.response.finance.FinancialReportExportFile;

public interface FinancialReportExportService {

  FinancialReportExportFile exportEstimatedNetProfitReport(String period, String format);
}
