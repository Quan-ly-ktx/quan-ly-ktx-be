package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VehicleChangeRequestStatus {
  PENDING("Chờ duyệt"),
  APPROVED("Đã duyệt"),
  REJECTED("Từ chối"),
  CANCELLED("Đã hủy");

  private final String displayName;
}
