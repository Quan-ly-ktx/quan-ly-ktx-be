package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TicketStatus {
  UNASSIGNED("Chưa phân công"),
  ASSIGNED("Mới giao"),
  IN_PROGRESS("Đang xử lý"),
  COMPLETED("Hoàn thành");

  private final String displayName;
}
