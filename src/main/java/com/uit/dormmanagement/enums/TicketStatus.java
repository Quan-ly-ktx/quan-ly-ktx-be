package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TicketStatus {
  OPEN("Mới giao"),
  IN_PROGRESS("Đang xử lý"),
  RESOLVED("Hoàn thành"),
  CANCELLED("Đã hủy");

  private final String displayName;
}
