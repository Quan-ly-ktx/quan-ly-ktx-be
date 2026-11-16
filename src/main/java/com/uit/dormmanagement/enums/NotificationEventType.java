package com.uit.dormmanagement.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationEventType {
  INVOICE_ISSUED("Phát hành hóa đơn"),
  INVOICE_DUE("Hóa đơn đến hạn"),
  INVOICE_OVERDUE("Hóa đơn quá hạn"),
  PAYMENT_CONFIRMED("Xác nhận thanh toán"),
  PAYMENT_PROOF_REJECTED("Từ chối minh chứng"),
  VIOLATION_CREATED("Lập biên bản vi phạm"),
  VEHICLE_REGISTRATION_REJECTED("Từ chối đăng ký xe"),
  VEHICLE_CHANGE_APPROVED("Duyệt đổi xe"),
  VEHICLE_CHANGE_REJECTED("Từ chối đổi xe");

  private final String displayName;
}
