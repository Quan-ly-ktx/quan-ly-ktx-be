package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InvoiceStatus {
  PAID("Đã thu"),
  PENDING_CONFIRMATION("Chờ xác nhận"),
  UNPAID("Nợ");

  private final String displayName;
}
