package com.uit.dormmanagement.configs;

import com.uit.dormmanagement.services.StorageService;
import com.uit.dormmanagement.services.impl.LocalStorageServiceImpl;
import com.uit.dormmanagement.services.impl.OciObjectStorageServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

  @Bean
  @ConditionalOnProperty(name = "oci.storage.enabled", havingValue = "true")
  public StorageService ociStorageService(
      @Value("${oci.storage.tenancy-id:}") String tenancyId,
      @Value("${oci.storage.user-id:}") String userId,
      @Value("${oci.storage.fingerprint:}") String fingerprint,
      @Value("${oci.storage.private-key-path:}") String privateKeyPath,
      @Value("${oci.storage.region:ap-singapore-1}") String region,
      @Value("${oci.storage.namespace:}") String namespace,
      @Value("${oci.storage.bucket-name:dormitory-storage}") String bucketName) {
    return new OciObjectStorageServiceImpl(
        tenancyId, userId, fingerprint, privateKeyPath, region, namespace, bucketName);
  }

  @Bean
  @ConditionalOnMissingBean(StorageService.class)
  public StorageService localStorageService(
      @Value("${storage.local.base-dir:uploads}") String baseDir) {
    return new LocalStorageServiceImpl(baseDir);
  }
}
