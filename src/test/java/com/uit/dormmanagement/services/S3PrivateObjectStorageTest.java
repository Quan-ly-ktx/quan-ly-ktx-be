package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.exceptions.error.SystemErrorCode;
import com.uit.dormmanagement.services.impl.S3PrivateObjectStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3PrivateObjectStorageTest {

  private static final String BUCKET = "private-dorm-bucket";

  @Mock private S3Client s3Client;

  private S3PrivateObjectStorage storage;

  @BeforeEach
  void setUp() {
    storage = new S3PrivateObjectStorage(s3Client, BUCKET);
  }

  @Test
  @DisplayName("putObject stores bytes with bucket, key, contentType, and contentLength")
  void putObject_shouldSendPutObjectRequest() throws IOException {
    byte[] data = "contract-pdf-bytes".getBytes(StandardCharsets.UTF_8);
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    storage.putObject(
        "contracts/123/file.pdf", "application/pdf", new ByteArrayInputStream(data), data.length);

    ArgumentCaptor<PutObjectRequest> reqCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
    ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
    verify(s3Client).putObject(reqCaptor.capture(), bodyCaptor.capture());

    assertThat(reqCaptor.getValue().bucket()).isEqualTo(BUCKET);
    assertThat(reqCaptor.getValue().key()).isEqualTo("contracts/123/file.pdf");
    assertThat(reqCaptor.getValue().contentType()).isEqualTo("application/pdf");
    assertThat(reqCaptor.getValue().contentLength()).isEqualTo(data.length);
    assertThat(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes())
        .isEqualTo(data);
  }

  @Test
  @DisplayName("getObject streams exact bytes, contentType, and contentLength")
  void getObject_shouldReturnStoredObjectContent() throws IOException {
    byte[] data = "vehicle-image-bytes".getBytes(StandardCharsets.UTF_8);
    GetObjectResponse metadata =
        GetObjectResponse.builder()
            .contentType("image/jpeg")
            .contentLength((long) data.length)
            .build();
    ResponseInputStream<GetObjectResponse> s3Stream =
        new ResponseInputStream<>(
            metadata, AbortableInputStream.create(new ByteArrayInputStream(data)));
    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(s3Stream);

    StoredObjectContent content = storage.getObject("vehicles/123/front.jpg");

    ArgumentCaptor<GetObjectRequest> reqCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(s3Client).getObject(reqCaptor.capture());
    assertThat(reqCaptor.getValue().bucket()).isEqualTo(BUCKET);
    assertThat(reqCaptor.getValue().key()).isEqualTo("vehicles/123/front.jpg");

    assertThat(content.contentType()).isEqualTo("image/jpeg");
    assertThat(content.contentLength()).isEqualTo(data.length);
    assertThat(content.resource().getInputStream().readAllBytes()).isEqualTo(data);
  }

  @Test
  @DisplayName(
      "getObject maps NoSuchKeyException and 404 S3Exception to ObjectStorageNotFoundException")
  void getObject_whenMissing_shouldThrowNotFound() {
    when(s3Client.getObject(any(GetObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("missing").build());

    assertThatThrownBy(() -> storage.getObject("contracts/missing.pdf"))
        .isInstanceOf(ObjectStorageNotFoundException.class);

    S3Exception notFound404 =
        (S3Exception)
            S3Exception.builder()
                .statusCode(404)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchKey").build())
                .build();
    when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(notFound404);

    assertThatThrownBy(() -> storage.getObject("contracts/missing2.pdf"))
        .isInstanceOf(ObjectStorageNotFoundException.class);
  }

  @Test
  @DisplayName(
      "S3 500 or SdkClientException maps cleanly to EXTERNAL_SERVICE_ERROR without leaking details")
  void s3Errors_shouldMapToExternalServiceError() {
    S3Exception internalS3Error =
        (S3Exception)
            S3Exception.builder()
                .statusCode(500)
                .message("https://secret-endpoint.example/ns")
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("InternalError").build())
                .build();
    when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(internalS3Error);

    assertThatThrownBy(() -> storage.getObject("contracts/err.pdf"))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex -> {
              BaseAppException bae = (BaseAppException) ex;
              assertThat(bae.getErrorCode()).isEqualTo(SystemErrorCode.EXTERNAL_SERVICE_ERROR);
              assertThat(bae.getMessage()).doesNotContain("secret-endpoint");
            });

    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(SdkClientException.create("network timeout to sensitive-host"));

    assertThatThrownBy(
            () -> storage.putObject("contracts/err.pdf", "application/pdf", new byte[] {1}))
        .isInstanceOf(BaseAppException.class)
        .satisfies(
            ex -> {
              BaseAppException bae = (BaseAppException) ex;
              assertThat(bae.getErrorCode()).isEqualTo(SystemErrorCode.EXTERNAL_SERVICE_ERROR);
              assertThat(bae.getMessage()).doesNotContain("sensitive-host");
            });
  }

  @Test
  @DisplayName("deleteObject deletes object and treats NoSuchKey/404 as idempotent")
  void deleteObject_shouldDeleteAndIgnoreMissing() {
    when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenReturn(DeleteObjectResponse.builder().build());

    storage.deleteObject("contracts/123/doc.pdf");

    ArgumentCaptor<DeleteObjectRequest> reqCaptor =
        ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3Client).deleteObject(reqCaptor.capture());
    assertThat(reqCaptor.getValue().bucket()).isEqualTo(BUCKET);
    assertThat(reqCaptor.getValue().key()).isEqualTo("contracts/123/doc.pdf");

    when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().build());
    storage.deleteObject("contracts/123/already-deleted.pdf");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "../secret.txt",
        "contracts/../secret.txt",
        "/leading-slash.pdf",
        "\\leading-backslash.pdf",
        "contracts\\win.pdf",
        "contracts//double.pdf",
        " leading-space.pdf",
        "trailing-space.pdf ",
        "bad\nkey.pdf"
      })
  @DisplayName("Invalid or traversal keys are rejected before calling S3Client")
  void invalidKeys_shouldBeRejected(String invalidKey) {
    assertThatThrownBy(() -> storage.getObject(invalidKey))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage.deleteObject(invalidKey))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage.putObject(invalidKey, "text/plain", new byte[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(s3Client);
  }

  @Test
  @DisplayName("Null key is rejected before calling S3Client")
  void nullKey_shouldBeRejected() {
    assertThatThrownBy(() -> storage.getObject(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage.deleteObject(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage.putObject(null, "text/plain", new byte[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(s3Client);
  }
}
