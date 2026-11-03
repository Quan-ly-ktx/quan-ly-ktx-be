package com.uit.dormmanagement.exceptions.error;

import com.uit.dormmanagement.exceptions.AppErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RoomErrorCode implements AppErrorCode {
  ROOM_NOT_FOUND("ROOM_404", "Không tìm thấy thông tin phòng tương ứng", HttpStatus.NOT_FOUND),
  BED_NOT_FOUND("BED_404", "Không tìm thấy vị trí giường tương ứng", HttpStatus.NOT_FOUND),
  ROOM_NUMBER_DUPLICATED("ROOM_409", "Số phòng đã tồn tại trong tòa nhà", HttpStatus.CONFLICT),
  GENDER_POLICY_MISMATCH(
      "ROOM_400_GENDER",
      "Chính sách giới tính phòng không khớp với giới tính khách thuê (BR-02)",
      HttpStatus.BAD_REQUEST),
  CANNOT_CHANGE_GENDER_POLICY(
      "ROOM_400_OCCUPIED",
      "Không thể đổi chính sách giới tính khi phòng đang có khách cư trú",
      HttpStatus.BAD_REQUEST),
  BED_NOT_AVAILABLE(
      "BED_400_STATUS", "Vị trí giường không ở trạng thái Sẵn sàng", HttpStatus.BAD_REQUEST);

  private final String errorCode;
  private final String message;
  private final HttpStatus httpStatus;
}
