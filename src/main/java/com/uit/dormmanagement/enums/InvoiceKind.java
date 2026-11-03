package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InvoiceKind {
  ALL_INCLUSIVE_RENT("Tiền phòng trọn gói"),
  OTHER("Phụ phí phát sinh");

  private final String displayName;
}
