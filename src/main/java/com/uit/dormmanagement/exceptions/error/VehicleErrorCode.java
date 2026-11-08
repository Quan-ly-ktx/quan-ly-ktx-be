package com.uit.dormmanagement.exceptions.error;

import com.uit.dormmanagement.exceptions.AppErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VehicleErrorCode implements AppErrorCode {
  VEHICLE_NOT_FOUND("VEHICLE_404", "Không tìm thấy thông tin xe tương ứng", HttpStatus.NOT_FOUND),
  LICENSE_PLATE_DUPLICATED(
      "VEHICLE_409_LICENSE_PLATE", "Biển số xe đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
  CARD_CODE_DUPLICATED(
      "VEHICLE_409_CARD_CODE", "Mã thẻ gửi xe đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
  VEHICLE_QUOTA_EXCEEDED(
      "VEHICLE_400_QUOTA",
      "Khách thuê đã đạt hạn mức đăng ký xe tối đa (tối đa 1 xe máy/xe đạp)",
      HttpStatus.BAD_REQUEST),
  VEHICLE_ALREADY_ACTIVE(
      "VEHICLE_400_ACTIVE",
      "Phương tiện đã được kích hoạt thẻ gửi xe trước đó",
      HttpStatus.BAD_REQUEST),
  VEHICLE_NOT_PENDING(
      "VEHICLE_400_NOT_PENDING",
      "Phương tiện không ở trạng thái chờ cấp thẻ (PENDING_APPROVAL)",
      HttpStatus.BAD_REQUEST);

  private final String errorCode;
  private final String message;
  private final HttpStatus httpStatus;
}
