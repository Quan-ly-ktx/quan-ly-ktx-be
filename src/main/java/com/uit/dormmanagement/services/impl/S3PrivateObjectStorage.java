package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.exceptions.error.SystemErrorCode;
import com.uit.dormmanagement.services.PrivateObjectStorage;
import com.uit.dormmanagement.services.StoredObjectContent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
public class S3PrivateObjectStorage implements PrivateObjectStorage {

  private final S3Client s3Client;
  private final String bucketName;

  public S3PrivateObjectStorage(S3Client s3Client, String bucketName) {
    this.s3Client = Objects.requireNonNull(s3Client, "S3Client must not be null");
    if (bucketName == null || bucketName.isBlank()) {
      throw new IllegalArgumentException("Bucket name must not be blank");
    }
    this.bucketName = bucketName.trim();
  }

  @Override
  public void putObject(String key, String contentType, InputStream content, long contentLength) {
    String validatedKey = PrivateObjectStorage.validateKey(key);
    if (content == null) {
      throw new IllegalArgumentException("Object content stream must not be null");
    }
    if (contentLength < 0) {
      throw new IllegalArgumentException("Object content length must be non-negative");
    }

    byte[] payload;
    try {
      payload = content.readAllBytes();
    } catch (IOException e) {
      throw new BaseAppException(
          SystemErrorCode.INTERNAL_SERVER_ERROR, "Không thể đọc dữ liệu tệp tải lên");
    }

    String effectiveContentType =
        (contentType != null && !contentType.isBlank())
            ? contentType.trim()
            : MediaType.APPLICATION_OCTET_STREAM_VALUE;

    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(bucketName)
            .key(validatedKey)
            .contentType(effectiveContentType)
            .contentLength((long) payload.length)
            .build();

    try {
      s3Client.putObject(request, RequestBody.fromBytes(payload));
    } catch (S3Exception e) {
      log.error("S3 putObject failed: status={}, code={}", e.statusCode(), errorCodeOf(e));
      throw new BaseAppException(
          SystemErrorCode.EXTERNAL_SERVICE_ERROR,
          "Không thể lưu tệp lên hệ thống lưu trữ đối tượng");
    } catch (SdkException e) {
      log.error("S3 putObject client error: type={}", e.getClass().getSimpleName());
      throw new BaseAppException(
          SystemErrorCode.EXTERNAL_SERVICE_ERROR,
          "Không thể lưu tệp lên hệ thống lưu trữ đối tượng");
    }
  }

  @Override
  public StoredObjectContent getObject(String key) {
    String validatedKey = PrivateObjectStorage.validateKey(key);
    GetObjectRequest request =
        GetObjectRequest.builder().bucket(bucketName).key(validatedKey).build();

    try {
      ResponseInputStream<GetObjectResponse> responseStream = s3Client.getObject(request);
      try {
        GetObjectResponse meta = responseStream.response();
        String contentType =
            (meta.contentType() != null && !meta.contentType().isBlank())
                ? meta.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        long contentLength = meta.contentLength() != null ? meta.contentLength() : -1L;
        return new StoredObjectContent(
            new ManagedStreamResource(responseStream, contentLength), contentType, contentLength);
      } catch (RuntimeException | Error ex) {
        abortOrCloseQuietly(responseStream);
        throw ex;
      }
    } catch (NoSuchKeyException e) {
      throw new ObjectStorageNotFoundException("Stored object not found");
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        throw new ObjectStorageNotFoundException("Stored object not found");
      }
      log.error("S3 getObject failed: status={}, code={}", e.statusCode(), errorCodeOf(e));
      throw new BaseAppException(
          SystemErrorCode.EXTERNAL_SERVICE_ERROR,
          "Không thể tải tệp từ hệ thống lưu trữ đối tượng");
    } catch (SdkException e) {
      log.error("S3 getObject client error: type={}", e.getClass().getSimpleName());
      throw new BaseAppException(
          SystemErrorCode.EXTERNAL_SERVICE_ERROR,
          "Không thể tải tệp từ hệ thống lưu trữ đối tượng");
    }
  }

  @Override
  public void deleteObject(String key) {
    String validatedKey = PrivateObjectStorage.validateKey(key);
    DeleteObjectRequest request =
        DeleteObjectRequest.builder().bucket(bucketName).key(validatedKey).build();

    try {
      s3Client.deleteObject(request);
    } catch (NoSuchKeyException e) {
      // Idempotent delete
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return;
      }
      log.error("S3 deleteObject failed: status={}, code={}", e.statusCode(), errorCodeOf(e));
      throw new BaseAppException(
          SystemErrorCode.EXTERNAL_SERVICE_ERROR,
          "Không thể xóa tệp trên hệ thống lưu trữ đối tượng");
    } catch (SdkException e) {
      log.error("S3 deleteObject client error: type={}", e.getClass().getSimpleName());
      throw new BaseAppException(
          SystemErrorCode.EXTERNAL_SERVICE_ERROR,
          "Không thể xóa tệp trên hệ thống lưu trữ đối tượng");
    }
  }

  private String errorCodeOf(S3Exception e) {
    return e.awsErrorDetails() != null && e.awsErrorDetails().errorCode() != null
        ? e.awsErrorDetails().errorCode()
        : "UNKNOWN";
  }

  static void abortOrCloseQuietly(InputStream stream) {
    if (stream == null) {
      return;
    }
    if (stream instanceof ResponseInputStream<?> responseStream) {
      try {
        responseStream.abort();
      } catch (RuntimeException ex) {
        log.debug(
            "Ignoring error while aborting S3 response stream: {}", ex.getClass().getSimpleName());
      }
    }
    try {
      stream.close();
    } catch (IOException | RuntimeException ex) {
      log.debug(
          "Ignoring error while closing S3 response stream: {}", ex.getClass().getSimpleName());
    }
  }

  public static final class ManagedStreamResource extends InputStreamResource
      implements AutoCloseable {

    private final InputStream delegate;
    private final long contentLength;
    private final AtomicBoolean consumed = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ManagedStreamResource(InputStream delegate) {
      this(delegate, -1L);
    }

    public ManagedStreamResource(InputStream delegate, long contentLength) {
      super(delegate);
      this.delegate = Objects.requireNonNull(delegate, "Response stream must not be null");
      this.contentLength = contentLength;
    }

    @Override
    public InputStream getInputStream() throws IOException, IllegalStateException {
      if (closed.get()) {
        throw new IllegalStateException("ManagedStreamResource has already been closed");
      }
      InputStream stream = super.getInputStream();
      consumed.set(true);
      return stream;
    }

    @Override
    public long contentLength() throws IOException {
      return contentLength >= 0 ? contentLength : super.contentLength();
    }

    @Override
    public void close() {
      if (!closed.compareAndSet(false, true)) {
        return;
      }
      if (!consumed.get()) {
        abortOrCloseQuietly(delegate);
      }
    }
  }
}
