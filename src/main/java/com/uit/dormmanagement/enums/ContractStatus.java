package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContractStatus {
  ACTIVE("Hiệu lực"),
  EXPIRING_SOON("Sắp hết hạn"),
  TERMINATED("Đã thanh lý"),
  CANCELLED("Đã hủy");

  private final String displayName;
}
