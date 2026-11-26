package com.uit.dormmanagement.configs;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.services.PrivateObjectStorage;
import com.uit.dormmanagement.services.VehicleEvidenceStorage;
import com.uit.dormmanagement.services.impl.PrivateVehicleEvidenceStorage;
import com.uit.dormmanagement.services.impl.S3PrivateObjectStorage;
import com.uit.dormmanagement.support.InMemoryPrivateObjectStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

class StorageConfigTest {

  private final ApplicationContextRunner s3ContextRunner =
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(StorageConfig.class));

  private final ApplicationContextRunner inMemoryContextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(InMemoryStorageTestConfig.class, StorageConfig.class));

  @Test
  @DisplayName(
      "Default s3 provider fails closed when required S3 properties are missing without leaking secrets")
  void s3Provider_whenMissingProperties_shouldFailClosedWithoutLeakingValues() {
    String dummySecret = "dummy-secret-never-leak-in-exception";
    String dummyAccessKey = "dummy-access-key-never-leak";
    String dummyEndpoint = "https://dummy-ns.compat.objectstorage.ap-singapore-1.oraclecloud.com";

    s3ContextRunner
        .withPropertyValues(
            "app.storage.s3.endpoint=" + dummyEndpoint,
            "app.storage.s3.region=ap-singapore-1",
            "app.storage.s3.access-key=" + dummyAccessKey,
            "app.storage.s3.secret-key=" + dummySecret)
        .run(
            context -> {
              assertThat(context).hasFailed();
              Throwable failure = context.getStartupFailure();
              String fullFailureText = collectMessages(failure);
              assertThat(fullFailureText)
                  .contains("Missing required S3 storage configuration properties")
                  .contains("app.storage.s3.bucket-name")
                  .doesNotContain(dummySecret)
                  .doesNotContain(dummyAccessKey)
                  .doesNotContain(dummyEndpoint);
            });
  }

  @Test
  @DisplayName("S3 provider fails closed on invalid endpoint URI without leaking URI value")
  void s3Provider_whenInvalidEndpoint_shouldFailClosedWithoutLeakingUri() {
    String badEndpoint = "not-a-valid-uri://bad host/secret";

    s3ContextRunner
        .withPropertyValues(
            "app.storage.provider=s3",
            "app.storage.s3.endpoint=" + badEndpoint,
            "app.storage.s3.region=ap-singapore-1",
            "app.storage.s3.bucket-name=dummy-bucket",
            "app.storage.s3.access-key=dummy-ak",
            "app.storage.s3.secret-key=dummy-sk")
        .run(
            context -> {
              assertThat(context).hasFailed();
              String fullFailureText = collectMessages(context.getStartupFailure());
              assertThat(fullFailureText)
                  .contains("Invalid S3 storage endpoint URI configuration")
                  .doesNotContain(badEndpoint)
                  .doesNotContain("dummy-sk")
                  .doesNotContain("dummy-ak");
            });
  }

  @Test
  @DisplayName(
      "S3 provider creates S3Client, S3PrivateObjectStorage, and PrivateVehicleEvidenceStorage")
  void s3Provider_whenValidProperties_shouldWireS3Beans() {
    s3ContextRunner
        .withPropertyValues(
            "app.storage.provider=s3",
            "app.storage.s3.bucket-namespace=dummy-namespace",
            "app.storage.s3.region=ap-singapore-1",
            "app.storage.s3.endpoint=https://dummy-namespace.compat.objectstorage.ap-singapore-1.oraclecloud.com",
            "app.storage.s3.bucket-name=dummy-bucket",
            "app.storage.s3.access-key=dummy-ak",
            "app.storage.s3.secret-key=dummy-sk")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(S3Client.class);
              assertThat(context).hasSingleBean(PrivateObjectStorage.class);
              assertThat(context)
                  .getBean(PrivateObjectStorage.class)
                  .isInstanceOf(S3PrivateObjectStorage.class);
              assertThat(context).hasSingleBean(VehicleEvidenceStorage.class);
              assertThat(context)
                  .getBean(VehicleEvidenceStorage.class)
                  .isInstanceOf(PrivateVehicleEvidenceStorage.class);
            });
  }

  @Test
  @DisplayName("in-memory provider creates InMemoryPrivateObjectStorage and skips S3Client")
  void inMemoryProvider_shouldWireInMemoryDoubleWithoutS3Client() {
    inMemoryContextRunner
        .withPropertyValues("app.storage.provider=in-memory")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(S3Client.class);
              assertThat(context).hasSingleBean(PrivateObjectStorage.class);
              assertThat(context)
                  .getBean(PrivateObjectStorage.class)
                  .isInstanceOf(InMemoryPrivateObjectStorage.class);
              assertThat(context).hasSingleBean(VehicleEvidenceStorage.class);
              assertThat(context)
                  .getBean(VehicleEvidenceStorage.class)
                  .isInstanceOf(PrivateVehicleEvidenceStorage.class);
            });
  }

  private String collectMessages(Throwable throwable) {
    StringBuilder sb = new StringBuilder();
    Throwable current = throwable;
    while (current != null) {
      if (current.getMessage() != null) {
        sb.append(current.getMessage()).append(' ');
      }
      current = current.getCause();
    }
    return sb.toString();
  }
}
