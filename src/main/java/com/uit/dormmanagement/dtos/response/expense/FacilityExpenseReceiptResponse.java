package com.uit.dormmanagement.dtos.response.expense;

import lombok.Builder;

@Builder
public record FacilityExpenseReceiptResponse(boolean available, String contentUrl) {}
