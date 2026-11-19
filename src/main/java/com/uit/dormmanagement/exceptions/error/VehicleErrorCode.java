package com.uit.dormmanagement.exceptions.error;

import com.uit.dormmanagement.exceptions.AppErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VehicleErrorCode implements AppErrorCode {
  VEHICLE_NOT_FOUND("VEHICLE_404", "Không tìm thấy thông tin xe tương ứng", HttpStatus.NOT_FOUND),
  CARD_CODE_DUPLICATED(
      "VEHICLE_409_CARD_CODE", "Mã thẻ gửi xe đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
  LICENSE_PLATE_DUPLICATED(
      "VEHICLE_409_LICENSE_PLATE", "Biển số xe đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
  VEHICLE_QUOTA_EXCEEDED(
      "VEHICLE_409_QUOTA",
      "Khách thuê đã đạt hạn mức đăng ký xe tối đa (tối đa 1 xe đang chờ duyệt hoặc đang sử dụng)",
      HttpStatus.CONFLICT),
  VEHICLE_ALREADY_ACTIVE(
      "VEHICLE_409_ACTIVE",
      "Phương tiện đã được kích hoạt thẻ gửi xe trước đó",
      HttpStatus.CONFLICT),
  VEHICLE_NOT_PENDING(
      "VEHICLE_409_NOT_PENDING",
      "Phương tiện không ở trạng thái chờ cấp thẻ (PENDING_CARD)",
      HttpStatus.CONFLICT),
  INVALID_VEHICLE_TYPE_DATA(
      "VEHICLE_400_INVALID_TYPE_DATA",
      "Dữ liệu đăng ký phương tiện không hợp lệ theo loại xe",
      HttpStatus.BAD_REQUEST),
  INVALID_EVIDENCE_FILE(
      "VEHICLE_400_INVALID_EVIDENCE",
      "File ảnh đăng ký xe không hợp lệ (chỉ chấp nhận JPEG, PNG, WebP tối đa 10MB)",
      HttpStatus.BAD_REQUEST),
  EVIDENCE_SIDE_CONFLICT(
      "VEHICLE_400_EVIDENCE_CONFLICT",
      "Không thể vừa tải lên ảnh mới vừa xóa ảnh cho cùng một mặt",
      HttpStatus.BAD_REQUEST),
  EVIDENCE_CLEAR_NOT_ALLOWED(
      "VEHICLE_400_EVIDENCE_CLEAR_NOT_ALLOWED",
      "Loại xe cơ giới bắt buộc phải có đủ ảnh đăng ký 2 mặt, không được xóa",
      HttpStatus.BAD_REQUEST),
  VEHICLE_INVALID_STATE_TRANSITION(
      "VEHICLE_409_INVALID_STATE",
      "Trạng thái phương tiện không hợp lệ để thực hiện thao tác này",
      HttpStatus.CONFLICT),
  VEHICLE_HAS_PENDING_CHANGE_REQUEST(
      "VEHICLE_409_PENDING_CHANGE_REQUEST",
      "Phương tiện đang có yêu cầu đổi xe chờ duyệt, không thể thu hồi thẻ",
      HttpStatus.CONFLICT),
  VEHICLE_CHANGE_REQUEST_NOT_FOUND(
      "VEHICLE_CHANGE_REQUEST_404", "Không tìm thấy yêu cầu đổi xe", HttpStatus.NOT_FOUND),
  VEHICLE_CHANGE_REQUEST_NOT_PENDING(
      "VEHICLE_CHANGE_REQUEST_409_NOT_PENDING",
      "Yêu cầu đổi xe không ở trạng thái chờ duyệt (PENDING)",
      HttpStatus.CONFLICT),
  VEHICLE_CHANGE_REQUEST_ALREADY_EXISTS(
      "VEHICLE_CHANGE_REQUEST_409_EXISTS",
      "Khách thuê đang có một yêu cầu đổi xe chờ xử lý",
      HttpStatus.CONFLICT),
  VEHICLE_EVIDENCE_NOT_FOUND(
      "VEHICLE_EVIDENCE_404", "Không tìm thấy ảnh chứng minh đăng ký xe", HttpStatus.NOT_FOUND);

  private final String errorCode;
  private final String message;
  private final HttpStatus httpStatus;
}
