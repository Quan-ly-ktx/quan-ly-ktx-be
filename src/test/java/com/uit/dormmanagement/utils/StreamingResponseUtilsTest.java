package com.uit.dormmanagement.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class StreamingResponseUtilsTest {

  @Test
  @DisplayName("ok with HEAD returns 200 with headers, null body, and closes AutoCloseable resource")
  void ok_withHead_shouldReturnHeadersEmptyBodyAndCloseResource() {
    TrackingAutoCloseableResource resource =
        new TrackingAutoCloseableResource("evidence".getBytes(StandardCharsets.UTF_8), false);

    ResponseEntity<Resource> response =
        StreamingResponseUtils.ok(resource, "image/jpeg", 8L, HttpMethod.HEAD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
    assertThat(response.getHeaders().getContentLength()).isEqualTo(8L);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isNull();
    assertThat(response.getBody()).isNull();
    assertThat(resource.closeCount.get()).isEqualTo(1);
    assertThat(resource.streamOpened.get()).isZero();
  }

  @Test
  @DisplayName(
      "okInline with HEAD returns 200 with inline Content-Disposition, null body, and closes resource")
  void okInline_withHead_shouldReturnInlineDispositionEmptyBodyAndCloseResource() {
    TrackingAutoCloseableResource resource =
        new TrackingAutoCloseableResource("pdf-data".getBytes(StandardCharsets.UTF_8), false);

    ResponseEntity<Resource> response =
        StreamingResponseUtils.okInline(
            resource, "application/pdf", 8L, "Hợp đồng thuê.pdf", HttpMethod.HEAD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(response.getHeaders().getContentLength()).isEqualTo(8L);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .isEqualTo("inline; filename=\"Hop_dong_thue.pdf\"");
    assertThat(response.getBody()).isNull();
    assertThat(resource.closeCount.get()).isEqualTo(1);
    assertThat(resource.streamOpened.get()).isZero();
  }

  @Test
  @DisplayName("HEAD with negative contentLength omits Content-Length header and closes resource")
  void headWithNegativeContentLength_shouldOmitLengthAndCloseResource() {
    TrackingAutoCloseableResource resource =
        new TrackingAutoCloseableResource("data".getBytes(StandardCharsets.UTF_8), false);

    ResponseEntity<Resource> response =
        StreamingResponseUtils.ok(resource, "image/png", -1L, HttpMethod.HEAD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
    assertThat(response.getHeaders().getContentLength()).isEqualTo(-1L);
    assertThat(response.getBody()).isNull();
    assertThat(resource.closeCount.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("Invalid contentType during header building closes resource before rethrowing")
  void invalidContentType_shouldCloseResourceBeforeRethrowing() {
    TrackingAutoCloseableResource resource1 =
        new TrackingAutoCloseableResource(new byte[] {1, 2}, false);
    assertThatThrownBy(
            () ->
                StreamingResponseUtils.ok(
                    resource1, "not-a-valid-media-type", 2L, HttpMethod.GET))
        .isInstanceOf(InvalidMediaTypeException.class);
    assertThat(resource1.closeCount.get()).isEqualTo(1);

    TrackingAutoCloseableResource resource2 =
        new TrackingAutoCloseableResource(new byte[] {1, 2}, true);
    assertThatThrownBy(
            () ->
                StreamingResponseUtils.okInline(
                    resource2, "bad-media-type", 2L, "contract.pdf", HttpMethod.HEAD))
        .isInstanceOf(InvalidMediaTypeException.class);
    assertThat(resource2.closeCount.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("GET or null HttpMethod keeps resource open for Spring streaming")
  void getOrNullMethod_shouldKeepResourceOpen() {
    TrackingAutoCloseableResource getResource =
        new TrackingAutoCloseableResource("get-body".getBytes(StandardCharsets.UTF_8), false);
    ResponseEntity<Resource> getResponse =
        StreamingResponseUtils.okInline(
            getResource, "application/pdf", 8L, "doc.pdf", HttpMethod.GET);

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody()).isSameAs(getResource);
    assertThat(getResponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .isEqualTo("inline; filename=\"doc.pdf\"");
    assertThat(getResource.closeCount.get()).isZero();

    TrackingAutoCloseableResource nullMethodResource =
        new TrackingAutoCloseableResource("null-method".getBytes(StandardCharsets.UTF_8), false);
    ResponseEntity<Resource> nullMethodResponse =
        StreamingResponseUtils.ok(nullMethodResource, "image/webp", 11L, null);

    assertThat(nullMethodResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(nullMethodResponse.getBody()).isSameAs(nullMethodResource);
    assertThat(nullMethodResource.closeCount.get()).isZero();
  }

  private static final class TrackingAutoCloseableResource extends InputStreamResource
      implements AutoCloseable {

    private final AtomicInteger closeCount = new AtomicInteger(0);
    private final AtomicInteger streamOpened = new AtomicInteger(0);
    private final boolean throwOnClose;

    private TrackingAutoCloseableResource(byte[] bytes, boolean throwOnClose) {
      super(new ByteArrayInputStream(bytes));
      this.throwOnClose = throwOnClose;
    }

    @Override
    public InputStream getInputStream() throws IOException, IllegalStateException {
      streamOpened.incrementAndGet();
      return super.getInputStream();
    }

    @Override
    public void close() throws IOException {
      closeCount.incrementAndGet();
      if (throwOnClose) {
        throw new IOException("simulated close failure");
      }
    }
  }
}
