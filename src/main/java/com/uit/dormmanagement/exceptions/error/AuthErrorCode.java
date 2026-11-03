package com.uit.dormmanagement.exceptions.error;

import com.uit.dormmanagement.exceptions.AppErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements AppErrorCode {
  INVALID_CREDENTIALS(
      "AUTH_401_CREDENTIALS",
      "Tên đăng nhập hoặc mật khẩu không chính xác",
      HttpStatus.UNAUTHORIZED),
  ACCOUNT_TEMPORARILY_LOCKED(
      "AUTH_403_RATE_LOCKED",
      "Tài khoản đã bị tạm khóa 15 phút do nhập sai mật khẩu quá 5 lần (FR-21)",
      HttpStatus.FORBIDDEN),
  ACCOUNT_LOCKED(
      "AUTH_403_LOCKED",
      "Tài khoản đã bị khóa bởi Quản trị viên. Vui lòng liên hệ Ban Quản lý",
      HttpStatus.FORBIDDEN),
  ACCOUNT_INACTIVE(
      "AUTH_403_INACTIVE",
      "Tài khoản chưa được kích hoạt hoặc đã bị vô hiệu hóa",
      HttpStatus.FORBIDDEN),
  INVALID_REFRESH_TOKEN(
      "AUTH_401_REFRESH", "Refresh token không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED),
  TOKEN_REVOKED(
      "AUTH_401_REVOKED", "Token đã bị thu hồi hoặc phiên đã đăng xuất", HttpStatus.UNAUTHORIZED),
  INVALID_OLD_PASSWORD(
      "AUTH_400_OLD_PASSWORD", "Mật khẩu hiện tại không chính xác", HttpStatus.BAD_REQUEST),
  PASSWORD_CONFIRM_MISMATCH(
      "AUTH_400_PASSWORD_CONFIRM", "Mật khẩu xác nhận không khớp", HttpStatus.BAD_REQUEST),
  PASSWORD_SAME_AS_OLD(
      "AUTH_400_PASSWORD_SAME",
      "Mật khẩu mới không được trùng với mật khẩu cũ",
      HttpStatus.BAD_REQUEST),
  INVALID_OTP("AUTH_400_OTP", "Mã OTP không chính xác hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
  INVALID_RESET_TOKEN(
      "AUTH_400_RESET_TOKEN",
      "Mã xác thực đặt lại mật khẩu không hợp lệ hoặc đã hết hạn",
      HttpStatus.BAD_REQUEST),
  OTP_COOLDOWN_ACTIVE(
      "AUTH_429_OTP_COOLDOWN",
      "Vui lòng đợi 60 giây trước khi yêu cầu gửi lại mã OTP",
      HttpStatus.TOO_MANY_REQUESTS),
  OTP_MAX_ATTEMPTS_EXCEEDED(
      "AUTH_429_OTP_ATTEMPTS",
      "Đã nhập sai OTP quá 3 lần. Vui lòng yêu cầu mã OTP mới",
      HttpStatus.TOO_MANY_REQUESTS),
  USER_NOT_FOUND("AUTH_404_USER", "Không tìm thấy thông tin người dùng", HttpStatus.NOT_FOUND),
  TECHNICIAN_NOT_FOUND("TECH_404", "Không tìm thấy thông tin kỹ thuật viên", HttpStatus.NOT_FOUND),
  DUPLICATE_EMAIL("AUTH_409_EMAIL", "Email đã được sử dụng trong hệ thống", HttpStatus.CONFLICT),
  DUPLICATE_PHONE(
      "AUTH_409_PHONE", "Số điện thoại đã được sử dụng trong hệ thống", HttpStatus.CONFLICT),
  DUPLICATE_USERNAME(
      "AUTH_409_USERNAME", "Tên đăng nhập đã tồn tại trong hệ thống", HttpStatus.CONFLICT);

  private final String errorCode;
  private final String message;
  private final HttpStatus httpStatus;
}
