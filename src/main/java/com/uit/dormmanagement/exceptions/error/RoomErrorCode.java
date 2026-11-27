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
  ROOM_CODE_PROTECTED(
      "ROOM_409_CODE_PROTECTED",
      "Mã phòng đã được sử dụng trong hồ sơ nghiệp vụ",
      HttpStatus.CONFLICT),
  AMENITY_NOT_FOUND(
      "ROOM_404_AMENITY", "Không tìm thấy một hoặc nhiều tiện nghi", HttpStatus.NOT_FOUND),
  GENDER_POLICY_MISMATCH(
      "ROOM_400_GENDER",
      "Chính sách giới tính phòng không khớp với giới tính khách thuê (BR-02)",
      HttpStatus.BAD_REQUEST),
  CANNOT_CHANGE_GENDER_POLICY(
      "ROOM_400_OCCUPIED",
      "Không thể đổi chính sách giới tính khi phòng đang có khách cư trú",
      HttpStatus.BAD_REQUEST),
  BED_NOT_AVAILABLE(
      "BED_400_STATUS", "Vị trí giường không ở trạng thái Sẵn sàng", HttpStatus.BAD_REQUEST),
  CANNOT_MAINTAIN_OCCUPIED_BED(
      "BED_400_MAINTENANCE",
      "Không thể chuyển giường đang có khách cư trú sang trạng thái bảo trì",
      HttpStatus.BAD_REQUEST),
  CANNOT_DELETE_OCCUPIED_ROOM(
      "ROOM_400_DELETE",
      "Không thể xóa phòng đang có khách cư trú hoặc hợp đồng còn hiệu lực",
      HttpStatus.BAD_REQUEST),
  ROOM_CAPACITY_CANNOT_BE_REDUCED(
      "ROOM_400_CAPACITY",
      "Sức chứa phòng không được phép giảm dưới số lượng người đang cư trú thực tế",
      HttpStatus.BAD_REQUEST),
  ROOM_ALREADY_ARCHIVED("ROOM_409_ARCHIVED", "Phòng đã ở trạng thái lưu trữ", HttpStatus.CONFLICT),
  ROOM_NOT_ARCHIVED(
      "ROOM_409_NOT_ARCHIVED", "Phòng không ở trạng thái lưu trữ", HttpStatus.CONFLICT),
  ROOM_HAS_HISTORY(
      "ROOM_409_HISTORY",
      "Phòng đã phát sinh lịch sử nghiệp vụ không thể xóa vĩnh viễn",
      HttpStatus.CONFLICT),
  ROOM_OCCUPIED_OR_CONTRACT_ACTIVE(
      "ROOM_409_OCCUPIED",
      "Phòng đang có khách cư trú hoặc hợp đồng còn hiệu lực",
      HttpStatus.CONFLICT),
  BED_INVALID_STATUS_TRANSITION(
      "BED_409_STATUS", "Chuyển đổi trạng thái giường không hợp lệ", HttpStatus.CONFLICT),
  BED_HAS_ACTIVE_CONTRACT(
      "BED_409_CONTRACT",
      "Giường đang có hợp đồng hiệu lực không thể chuyển trạng thái",
      HttpStatus.CONFLICT);

  private final String errorCode;
  private final String message;
  private final HttpStatus httpStatus;
}
