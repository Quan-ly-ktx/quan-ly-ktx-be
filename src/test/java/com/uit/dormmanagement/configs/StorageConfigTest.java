package com.uit.dormmanagement.configs;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.services.StorageService;
import com.uit.dormmanagement.services.impl.LocalStorageServiceImpl;
import com.uit.dormmanagement.services.impl.OciObjectStorageServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StorageConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(StorageConfig.class));

  @Test
  @DisplayName("Default configuration should load LocalStorageServiceImpl")
  void shouldLoadLocalStorageServiceByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(StorageService.class);
          assertThat(context)
              .getBean(StorageService.class)
              .isInstanceOf(LocalStorageServiceImpl.class);
        });
  }

  @Test
  @DisplayName("When oci.storage.enabled=false should load LocalStorageServiceImpl")
  void shouldLoadLocalStorageServiceWhenOciDisabled() {
    contextRunner
        .withPropertyValues("oci.storage.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(StorageService.class);
              assertThat(context)
                  .getBean(StorageService.class)
                  .isInstanceOf(LocalStorageServiceImpl.class);
            });
  }

  @Test
  @DisplayName("When oci.storage.enabled=true should load OciObjectStorageServiceImpl")
  void shouldLoadOciObjectStorageServiceWhenOciEnabled() {
    contextRunner
        .withPropertyValues(
            "oci.storage.enabled=true",
            "oci.storage.region=ap-singapore-1",
            "oci.storage.namespace=test-namespace",
            "oci.storage.bucket-name=test-bucket")
        .run(
            context -> {
              assertThat(context).hasSingleBean(StorageService.class);
              assertThat(context)
                  .getBean(StorageService.class)
                  .isInstanceOf(OciObjectStorageServiceImpl.class);
            });
  }
}
