package com.uit.dormmanagement.exceptions.error;

import com.uit.dormmanagement.exceptions.AppErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ContractErrorCode implements AppErrorCode {
  CONTRACT_NOT_FOUND("CONTRACT_404", "Không tìm thấy hợp đồng tương ứng", HttpStatus.NOT_FOUND),
  CONTRACT_ALREADY_TERMINATED(
      "CONTRACT_400_TERMINATED",
      "Hợp đồng đã chấm dứt hiệu lực hoặc đã thanh lý",
      HttpStatus.BAD_REQUEST),
  BED_NOT_AVAILABLE(
      "CONTRACT_400_BED_UNAVAILABLE",
      "Giường không ở trạng thái khả dụng để tạo hợp đồng",
      HttpStatus.BAD_REQUEST),
  GENDER_MISMATCH(
      "CONTRACT_400_GENDER_MISMATCH",
      "Giới tính của khách thuê không khớp với chính sách giới tính của phòng",
      HttpStatus.BAD_REQUEST),
  INVALID_CONTRACT_DATES(
      "CONTRACT_400_DATES",
      "Khoảng thời gian hiệu lực của hợp đồng không hợp lệ",
      HttpStatus.BAD_REQUEST),
  ONBOARDING_ONLY_ON_DAY_ONE(
      "CONTRACT_400_DAY_ONE_ONLY",
      "Chỉ được thực hiện tiếp nhận khách vào ngày 01 hằng tháng theo múi giờ cơ sở",
      HttpStatus.BAD_REQUEST),
  INVALID_DEPOSIT_AMOUNT(
      "CONTRACT_400_DEPOSIT",
      "Tiền đặt cọc phải là số nguyên VND không âm",
      HttpStatus.BAD_REQUEST),
  BED_ALREADY_RENTED(
      "CONTRACT_409_BED_OCCUPIED",
      "Giường đang có hợp đồng chiếm chỗ còn hiệu lực",
      HttpStatus.CONFLICT),
  TENANT_ALREADY_HAS_ACTIVE_CONTRACT(
      "CONTRACT_409_TENANT_ACTIVE",
      "Khách thuê đã có hợp đồng chiếm chỗ còn hiệu lực",
      HttpStatus.CONFLICT),
  TENANT_HAS_UNSETTLED_INVOICES(
      "CONTRACT_409_UNSETTLED_DEBT",
      "Khách thuê còn hóa đơn chưa tất toán (Nợ hoặc Chờ xác nhận)",
      HttpStatus.CONFLICT),
  ROOM_ARCHIVED(
      "CONTRACT_409_ROOM_ARCHIVED",
      "Phòng đang ở trạng thái lưu trữ, không thể tiếp nhận khách mới",
      HttpStatus.CONFLICT),
  DOCUMENT_NOT_FOUND(
      "CONTRACT_404_DOCUMENT", "Không tìm thấy tài liệu hợp đồng tương ứng", HttpStatus.NOT_FOUND),
  DOCUMENT_FILE_SIZE_EXCEEDED(
      "CONTRACT_400_FILE_SIZE",
      "Kích thước tài liệu đính kèm vượt quá giới hạn cho phép",
      HttpStatus.BAD_REQUEST),
  UNSUPPORTED_DOCUMENT_FORMAT(
      "CONTRACT_400_FILE_FORMAT",
      "Định dạng tập tin tài liệu không được hỗ trợ",
      HttpStatus.BAD_REQUEST);

  private final String errorCode;
  private final String message;
  private final HttpStatus httpStatus;
}
