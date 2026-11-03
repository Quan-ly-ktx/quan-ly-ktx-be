package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentMethod {
  VIETQR("Trực tuyến VietQR"),
  CASH("Tiền mặt"),
  BANK_TRANSFER("Chuyển khoản thủ công");

  private final String displayName;
}
