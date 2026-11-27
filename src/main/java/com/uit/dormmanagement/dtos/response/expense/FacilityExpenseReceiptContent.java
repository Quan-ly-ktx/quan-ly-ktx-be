package com.uit.dormmanagement.dtos.response.expense;

import lombok.Builder;
import org.springframework.core.io.Resource;

@Builder
public record FacilityExpenseReceiptContent(
    Resource resource, String contentType, long contentLength, String filename) {}
