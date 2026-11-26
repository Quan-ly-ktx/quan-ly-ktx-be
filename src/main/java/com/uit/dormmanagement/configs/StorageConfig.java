package com.uit.dormmanagement.configs;

import com.uit.dormmanagement.services.PrivateObjectStorage;
import com.uit.dormmanagement.services.VehicleEvidenceStorage;
import com.uit.dormmanagement.services.impl.PrivateVehicleEvidenceStorage;
import com.uit.dormmanagement.services.impl.S3PrivateObjectStorage;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3", matchIfMissing = true)
  public S3Client s3Client(StorageProperties properties) {
    StorageProperties.S3Properties s3 =
        properties != null && properties.getS3() != null
            ? properties.getS3()
            : new StorageProperties.S3Properties();
    validateS3Properties(s3);
    return buildS3Client(s3.getEndpoint(), s3.getRegion(), s3.getAccessKey(), s3.getSecretKey());
  }

  @Bean
  @ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3", matchIfMissing = true)
  @ConditionalOnMissingBean(PrivateObjectStorage.class)
  public PrivateObjectStorage s3PrivateObjectStorage(
      S3Client s3Client, StorageProperties properties) {
    return new S3PrivateObjectStorage(s3Client, properties.getS3().getBucketName().trim());
  }

  @Bean
  @ConditionalOnMissingBean(VehicleEvidenceStorage.class)
  public VehicleEvidenceStorage vehicleEvidenceStorage(PrivateObjectStorage privateObjectStorage) {
    return new PrivateVehicleEvidenceStorage(privateObjectStorage);
  }

  public static S3Client buildS3Client(
      String endpoint, String region, String accessKey, String secretKey) {
    if (isBlank(endpoint) || isBlank(region) || isBlank(accessKey) || isBlank(secretKey)) {
      throw new IllegalStateException("Missing required S3 storage configuration properties");
    }

    URI endpointUri;
    try {
      endpointUri = URI.create(endpoint.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Invalid S3 storage endpoint URI configuration");
    }

    String scheme = endpointUri.getScheme();
    if (scheme == null
        || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))
        || isBlank(endpointUri.getHost())) {
      throw new IllegalStateException("Invalid S3 storage endpoint URI configuration");
    }

    S3Configuration s3Configuration =
        S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .chunkedEncodingEnabled(false)
            .build();

    return S3Client.builder()
        .endpointOverride(endpointUri)
        .region(Region.of(region.trim()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey.trim(), secretKey.trim())))
        .serviceConfiguration(s3Configuration)
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .build();
  }

  private static void validateS3Properties(StorageProperties.S3Properties s3) {
    List<String> missing = new ArrayList<>();
    if (isBlank(s3.getEndpoint())) {
      missing.add("app.storage.s3.endpoint");
    }
    if (isBlank(s3.getRegion())) {
      missing.add("app.storage.s3.region");
    }
    if (isBlank(s3.getBucketName())) {
      missing.add("app.storage.s3.bucket-name");
    }
    if (isBlank(s3.getAccessKey())) {
      missing.add("app.storage.s3.access-key");
    }
    if (isBlank(s3.getSecretKey())) {
      missing.add("app.storage.s3.secret-key");
    }
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Missing required S3 storage configuration properties: " + String.join(", ", missing));
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
