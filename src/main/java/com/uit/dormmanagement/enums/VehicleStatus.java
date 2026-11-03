package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VehicleStatus {
  PENDING("Chưa cấp thẻ"),
  ACTIVE("Đã cấp thẻ"),
  REVOKED("Đã thu hồi");

  private final String displayName;
}
