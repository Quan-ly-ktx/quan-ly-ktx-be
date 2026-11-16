package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExpenseType {
  ELECTRICITY("Điện EVN"),
  WATER("Nước Sawaco"),
  INTERNET("Internet cáp quang"),
  SALARY("Tổng lương nhân viên"),
  OTHER("Chi phí khác");

  private final String displayName;
}
