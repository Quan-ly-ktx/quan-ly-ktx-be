package com.uit.dormmanagement.services;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public interface PrivateObjectStorage {

  void putObject(String key, String contentType, InputStream content, long contentLength);

  default void putObject(String key, String contentType, byte[] content) {
    if (content == null) {
      throw new IllegalArgumentException("Object content must not be null");
    }
    putObject(key, contentType, new ByteArrayInputStream(content), content.length);
  }

  StoredObjectContent getObject(String key);

  void deleteObject(String key);

  static String validateKey(String key) {
    if (key == null || key.isBlank() || !key.trim().equals(key)) {
      throw new IllegalArgumentException("Invalid object storage key");
    }
    if (key.startsWith("/")
        || key.startsWith("\\")
        || key.contains("\\")
        || key.contains("..")
        || key.contains("//")
        || key.indexOf('\0') >= 0
        || key.indexOf('\r') >= 0
        || key.indexOf('\n') >= 0) {
      throw new IllegalArgumentException("Invalid object storage key");
    }
    return key;
  }
}
