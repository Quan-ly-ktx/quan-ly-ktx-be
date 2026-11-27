package com.uit.dormmanagement.exceptions.error;

import com.uit.dormmanagement.exceptions.AppErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InvoiceErrorCode implements AppErrorCode {
  INVOICE_NOT_FOUND("INVOICE_404", "Không tìm thấy hóa đơn tương ứng", HttpStatus.NOT_FOUND),
  INVOICE_PROOF_NOT_FOUND(
      "INVOICE_404_PROOF",
      "Không tìm thấy ảnh minh chứng thanh toán của hóa đơn",
      HttpStatus.NOT_FOUND),
  INVALID_BILLING_PERIOD(
      "INVOICE_400_INVALID_PERIOD",
      "Kỳ hóa đơn không hợp lệ (phải là ngày 01 của tháng)",
      HttpStatus.BAD_REQUEST),
  FUTURE_BILLING_PERIOD_NOT_ALLOWED(
      "INVOICE_400_FUTURE_PERIOD",
      "Không thể phát hành hóa đơn cho kỳ tương lai xa",
      HttpStatus.BAD_REQUEST),
  ISSUANCE_WINDOW_NOT_OPEN(
      "INVOICE_400_ISSUANCE_WINDOW",
      "Kỳ hóa đơn tháng kế tiếp chỉ được phát hành từ ngày 25 hằng tháng",
      HttpStatus.BAD_REQUEST),
  PROOF_FILE_REQUIRED(
      "INVOICE_400_PROOF_REQUIRED",
      "Vui lòng đính kèm ảnh minh chứng chuyển khoản",
      HttpStatus.BAD_REQUEST),
  PROOF_FILE_SIZE_EXCEEDED(
      "INVOICE_400_PROOF_SIZE",
      "Kích thước ảnh minh chứng vượt quá giới hạn tối đa 5 MB",
      HttpStatus.BAD_REQUEST),
  UNSUPPORTED_PROOF_FORMAT(
      "INVOICE_400_PROOF_FORMAT",
      "Ảnh minh chứng phải có định dạng JPG, PNG hoặc WEBP hợp lệ",
      HttpStatus.BAD_REQUEST),
  MANAGER_STAFF_PROFILE_REQUIRED(
      "INVOICE_403_STAFF_PROFILE",
      "Tài khoản quản lý chưa được liên kết hồ sơ nhân sự để xác nhận thanh toán",
      HttpStatus.FORBIDDEN),
  INVOICE_ALREADY_PAID(
      "INVOICE_409_ALREADY_PAID",
      "Hóa đơn đã được xác nhận thanh toán trước đó",
      HttpStatus.CONFLICT),
  INVOICE_NOT_DEBT(
      "INVOICE_409_NOT_DEBT",
      "Thao tác chỉ áp dụng cho hóa đơn đang ở trạng thái Nợ",
      HttpStatus.CONFLICT),
  INVOICE_NOT_PENDING_CONFIRMATION(
      "INVOICE_409_NOT_PENDING",
      "Thao tác chỉ áp dụng cho hóa đơn đang ở trạng thái Chờ xác nhận",
      HttpStatus.CONFLICT),
  DUPLICATE_INVOICE_PERIOD(
      "INVOICE_409_DUPLICATE_PERIOD",
      "Hóa đơn cho hợp đồng và kỳ này đã tồn tại",
      HttpStatus.CONFLICT);

  private final String errorCode;
  private final String message;
  private final HttpStatus httpStatus;
}
