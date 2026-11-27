package com.uit.dormmanagement.dtos.response.finance;

import lombok.Builder;

@Builder
public record FinancialReportExportFile(byte[] content, String contentType, String filename) {}
