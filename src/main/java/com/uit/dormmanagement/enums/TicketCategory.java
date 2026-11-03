package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TicketCategory {
  PLUMBING("Điện nước"),
  AIR_CONDITIONING("Điều hòa"),
  ELECTRICAL("Điện"),
  FURNITURE("Nội thất"),
  LOCKS("Khóa cửa"),
  OTHER("Khác");

  private final String displayName;
}
