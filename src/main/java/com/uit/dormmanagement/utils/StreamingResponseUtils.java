package com.uit.dormmanagement.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Slf4j
public final class StreamingResponseUtils {

  private StreamingResponseUtils() {}

  public static ResponseEntity<Resource> ok(
      Resource resource, String contentType, long contentLength, HttpMethod httpMethod) {
    return build(resource, contentType, contentLength, false, null, httpMethod);
  }

  public static ResponseEntity<Resource> okInline(
      Resource resource,
      String contentType,
      long contentLength,
      String originalFilename,
      HttpMethod httpMethod) {
    return build(resource, contentType, contentLength, true, originalFilename, httpMethod);
  }

  public static void closeQuietly(Resource resource) {
    if (resource instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception ex) {
        log.debug(
            "Ignoring error while closing streaming resource: {}",
            ex.getClass().getSimpleName());
      }
    }
  }

  private static ResponseEntity<Resource> build(
      Resource resource,
      String contentType,
      long contentLength,
      boolean includeInlineDisposition,
      String originalFilename,
      HttpMethod httpMethod) {
    try {
      ResponseEntity.BodyBuilder builder =
          ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType));
      if (includeInlineDisposition) {
        builder.header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDispositionUtils.inline(originalFilename).toString());
      }
      if (contentLength >= 0) {
        builder.contentLength(contentLength);
      }
      if (HttpMethod.HEAD.equals(httpMethod)) {
        closeQuietly(resource);
        return builder.build();
      }
      return builder.body(resource);
    } catch (RuntimeException | Error ex) {
      closeQuietly(resource);
      throw ex;
    }
  }
}
