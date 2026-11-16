package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContractStatus {
  ACTIVE("Hiệu lực"),
  LIQUIDATED("Đã thanh lý");

  private final String displayName;
}
