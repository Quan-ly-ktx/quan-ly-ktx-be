package com.uit.dormmanagement.exceptions.error;

import com.uit.dormmanagement.exceptions.AppErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExpenseErrorCode implements AppErrorCode {
  EXPENSE_NOT_FOUND("EXPENSE_404", "Không tìm thấy chi phí vận hành", HttpStatus.NOT_FOUND),
  EXPENSE_RECEIPT_NOT_FOUND(
      "EXPENSE_404_RECEIPT",
      "Không tìm thấy hóa đơn/chứng từ của chi phí vận hành",
      HttpStatus.NOT_FOUND),
  INVALID_REPORT_PERIOD(
      "EXPENSE_400_INVALID_PERIOD",
      "Kỳ báo cáo không hợp lệ (định dạng hợp lệ: YYYY-MM)",
      HttpStatus.BAD_REQUEST),
  INVALID_EXPENSE_AMOUNT(
      "EXPENSE_400_AMOUNT",
      "Số tiền chi phí không hợp lệ (phải là số nguyên dương)",
      HttpStatus.BAD_REQUEST),
  INVALID_EXPORT_FORMAT(
      "EXPENSE_400_EXPORT_FORMAT",
      "Định dạng xuất báo cáo không hợp lệ (hỗ trợ: PDF, EXCEL)",
      HttpStatus.BAD_REQUEST),
  RECEIPT_FILE_REQUIRED(
      "EXPENSE_400_RECEIPT_REQUIRED",
      "Tệp hóa đơn/chứng từ không được để trống",
      HttpStatus.BAD_REQUEST),
  RECEIPT_FILE_SIZE_EXCEEDED(
      "EXPENSE_400_RECEIPT_SIZE",
      "Dung lượng tệp chứng từ vượt quá giới hạn cho phép (tối đa 5MB)",
      HttpStatus.BAD_REQUEST),
  UNSUPPORTED_RECEIPT_FORMAT(
      "EXPENSE_400_RECEIPT_FORMAT",
      "Định dạng tệp chứng từ không được hỗ trợ (chỉ chấp nhận JPG, JPEG, PNG, WEBP, PDF)",
      HttpStatus.BAD_REQUEST),
  MANAGER_STAFF_PROFILE_REQUIRED(
      "EXPENSE_403_STAFF_PROFILE",
      "Yêu cầu tài khoản Quản lý có hồ sơ nhân viên hợp lệ",
      HttpStatus.FORBIDDEN),
  SALARY_EXPENSE_ALREADY_EXISTS(
      "EXPENSE_409_SALARY_MONTH",
      "Chi phí lương (SALARY) đã được ghi nhận trong tháng này",
      HttpStatus.CONFLICT);

  private final String errorCode;
  private final String message;
  private final HttpStatus httpStatus;
}
