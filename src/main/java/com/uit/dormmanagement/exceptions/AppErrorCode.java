package com.uit.dormmanagement.exceptions;

import org.springframework.http.HttpStatus;

public interface AppErrorCode {
  String getErrorCode();

  String getMessage();

  HttpStatus getHttpStatus();
}
