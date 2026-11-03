package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ViolationLevel {
  REMINDER("Nhắc nhở"),
  WARNING("Cảnh cáo"),
  FINE("Phạt tiền");

  private final String displayName;
}
