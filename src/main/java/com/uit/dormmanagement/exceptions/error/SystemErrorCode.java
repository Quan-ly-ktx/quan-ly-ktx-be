package com.uit.dormmanagement.exceptions.error;

import com.uit.dormmanagement.exceptions.AppErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SystemErrorCode implements AppErrorCode {
  INTERNAL_SERVER_ERROR(
      "SYS_500", "Đã có lỗi hệ thống phát sinh", HttpStatus.INTERNAL_SERVER_ERROR),
  DATABASE_ERROR("SYS_501", "Thao tác cơ sở dữ liệu thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
  REDIS_ERROR("SYS_502", "Thao tác bộ nhớ đệm Redis thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
  EXTERNAL_SERVICE_ERROR("SYS_503", "Kết nối dịch vụ bên thứ ba thất bại", HttpStatus.BAD_GATEWAY);

  private final String errorCode;
  private final String message;
  private final HttpStatus httpStatus;
}
