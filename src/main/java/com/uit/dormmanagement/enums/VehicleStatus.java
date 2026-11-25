package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VehicleStatus {
  PENDING_CARD("Chờ cấp thẻ"),
  ISSUED("Đã cấp thẻ"),
  REJECTED("Bị từ chối"),
  REVOKED("Đã thu hồi"),
  REPLACED("Đã thay thế"),
  CANCELLED("Đã hủy");

  private final String displayName;
}
