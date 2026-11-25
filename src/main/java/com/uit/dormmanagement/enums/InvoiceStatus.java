package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InvoiceStatus {
  DEBT("Nợ"),
  PENDING_CONFIRMATION("Chờ xác nhận"),
  PAID("Đã thu");

  private final String displayName;
}
