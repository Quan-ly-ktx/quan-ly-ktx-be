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
