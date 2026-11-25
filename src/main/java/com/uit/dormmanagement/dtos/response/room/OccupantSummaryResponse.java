package com.uit.dormmanagement.dtos.response.room;

import java.time.LocalDate;
import java.util.UUID;

public record OccupantSummaryResponse(
    UUID tenantId,
    String fullName,
    String phone,
    String idCardNumber,
    LocalDate contractEndDate) {

  public OccupantSummaryResponse(
      UUID tenantId, String fullName, String phone, LocalDate contractEndDate) {
    this(tenantId, fullName, phone, null, contractEndDate);
  }
}
