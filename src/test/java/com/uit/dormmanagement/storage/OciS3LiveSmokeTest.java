package com.uit.dormmanagement.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.configs.StorageConfig;
import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.services.StoredObjectContent;
import com.uit.dormmanagement.services.impl.S3PrivateObjectStorage;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.services.s3.S3Client;

@Tag("oci-smoke")
@EnabledIfEnvironmentVariable(named = "OCI_SMOKE_TEST_ENABLED", matches = "true")
class OciS3LiveSmokeTest {

  @Test
  @DisplayName("Live OCI S3 private bucket supports putObject, getObject, and deleteObject")
  void liveOciPrivateBucket_roundTripUploadReadDelete() throws IOException {
    Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    String bucketNamespace = resolveRequired("S3_BUCKET_NAMESPACE", dotenv);
    String region = resolveRequired("S3_REGION", dotenv);
    String endpoint = resolveRequired("S3_ENDPOINT", dotenv);
    String bucketName = resolveRequired("S3_BUCKET_NAME", dotenv);
    String accessKey = resolveRequired("S3_ACCESS_KEY", dotenv);
    String secretKey = resolveRequired("S3_SECRET_KEY", dotenv);

    assertThat(bucketNamespace).isNotBlank();

    String objectKey = "smoke-tests/" + UUID.randomUUID() + ".txt";
    byte[] payload = "ktx-hub-phase3a-oci-smoke-check\n".getBytes(StandardCharsets.UTF_8);

    try (S3Client s3Client = StorageConfig.buildS3Client(endpoint, region, accessKey, secretKey)) {
      S3PrivateObjectStorage storage = new S3PrivateObjectStorage(s3Client, bucketName);
      boolean deleted = false;

      try {
        storage.putObject(objectKey, "text/plain", payload);

        StoredObjectContent loaded = storage.getObject(objectKey);
        byte[] downloadedBytes;
        try (InputStream is = loaded.resource().getInputStream()) {
          downloadedBytes = is.readAllBytes();
        }

        assertThat(downloadedBytes).isEqualTo(payload);
        assertThat(loaded.contentLength()).isEqualTo(payload.length);
        assertThat(loaded.contentType()).startsWith("text/plain");

        storage.deleteObject(objectKey);
        deleted = true;

        assertThatThrownBy(() -> storage.getObject(objectKey))
            .isInstanceOf(ObjectStorageNotFoundException.class);
      } finally {
        if (!deleted) {
          try {
            storage.deleteObject(objectKey);
          } catch (RuntimeException ignored) {
            // Best-effort smoke object cleanup
          }
        }
      }
    }
  }

  private static String resolveRequired(String name, Dotenv dotenv) {
    String envValue = System.getenv(name);
    String value = (envValue != null && !envValue.isBlank()) ? envValue : dotenv.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "Missing required environment variable for smoke test: " + name);
    }
    return value;
  }
}
