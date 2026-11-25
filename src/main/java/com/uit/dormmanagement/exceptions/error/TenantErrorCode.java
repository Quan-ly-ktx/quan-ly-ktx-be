package com.uit.dormmanagement.exceptions.error;

import com.uit.dormmanagement.exceptions.AppErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TenantErrorCode implements AppErrorCode {
  TENANT_NOT_FOUND(
      "TENANT_404", "Không tìm thấy thông tin khách thuê tương ứng", HttpStatus.NOT_FOUND),
  ID_CARD_NUMBER_DUPLICATED(
      "TENANT_409_ID_CARD", "Số căn cước công dân đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
  EMAIL_DUPLICATED(
      "TENANT_409_EMAIL", "Email khách thuê đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
  PHONE_DUPLICATED(
      "TENANT_409_PHONE", "Số điện thoại đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
  INVALID_ID_CARD_FORMAT(
      "TENANT_400_ID_CARD_FORMAT",
      "Định dạng số CCCD không hợp lệ (yêu cầu 12 chữ số)",
      HttpStatus.BAD_REQUEST),
  TENANT_NOT_ACTIVE(
      "TENANT_400_NOT_ACTIVE", "Khách thuê không ở trạng thái hoạt động", HttpStatus.BAD_REQUEST),
  GENDER_MISMATCH_CURRENT_ROOM(
      "TENANT_409_GENDER",
      "Giới tính không khớp với chính sách giới tính của phòng đang thuê",
      HttpStatus.CONFLICT),
  NO_CONTACT_FIELDS_PROVIDED(
      "TENANT_400_NO_FIELDS",
      "Phải cung cấp ít nhất một trường liên lạc cần cập nhật",
      HttpStatus.BAD_REQUEST);

  private final String errorCode;
  private final String message;
  private final HttpStatus httpStatus;
}
