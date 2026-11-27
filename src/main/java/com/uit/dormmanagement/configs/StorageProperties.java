package com.uit.dormmanagement.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

  private String provider = "s3";
  private S3Properties s3 = new S3Properties();

  @Override
  public String toString() {
    return "StorageProperties[provider=" + provider + "]";
  }

  @Getter
  @Setter
  public static class S3Properties {
    private String bucketNamespace;
    private String region;
    private String endpoint;
    private String bucketName;
    private String accessKey;
    private String secretKey;

    @Override
    public String toString() {
      return "S3Properties[redacted]";
    }
  }
}
