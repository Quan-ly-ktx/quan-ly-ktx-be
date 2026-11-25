package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentMethod {
  CASH("Tiền mặt"),
  VIETQR_TRANSFER("Chuyển khoản VietQR");

  private final String displayName;
}
