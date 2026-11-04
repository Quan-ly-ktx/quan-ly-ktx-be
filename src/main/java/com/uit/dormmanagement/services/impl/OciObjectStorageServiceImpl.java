package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.WebErrorCode;
import com.uit.dormmanagement.services.StorageService;
import java.nio.file.Paths;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Getter
public class OciObjectStorageServiceImpl implements StorageService {

  private final String tenancyId;
  private final String userId;
  private final String fingerprint;
  private final String privateKeyPath;
  private final String region;
  private final String namespace;
  private final String bucketName;

  public OciObjectStorageServiceImpl(
      String tenancyId,
      String userId,
      String fingerprint,
      String privateKeyPath,
      String region,
      String namespace,
      String bucketName) {
    this.tenancyId = tenancyId;
    this.userId = userId;
    this.fingerprint = fingerprint;
    this.privateKeyPath = privateKeyPath;
    this.region = (region != null && !region.isBlank()) ? region : "ap-singapore-1";
    this.namespace = namespace;
    this.bucketName =
        (bucketName != null && !bucketName.isBlank()) ? bucketName : "dormitory-storage";

    log.info(
        "Initialized OCI Object Storage Service for bucket: '{}' in region: '{}', namespace: '{}'",
        this.bucketName,
        this.region,
        this.namespace);
  }

  @Override
  public String uploadFile(MultipartFile file, String folder) {
    if (file == null || file.isEmpty()) {
      throw new BaseAppException(WebErrorCode.BAD_REQUEST, "Tệp tải lên không được để trống");
    }

    String originalFilename = file.getOriginalFilename();
    String extension = "";
    String cleanBaseName = "file";

    if (originalFilename != null && !originalFilename.isBlank()) {
      String fileNameOnly = Paths.get(originalFilename).getFileName().toString();
      int dotIndex = fileNameOnly.lastIndexOf('.');
      if (dotIndex >= 0) {
        extension = fileNameOnly.substring(dotIndex);
        cleanBaseName = fileNameOnly.substring(0, dotIndex).replaceAll("[^a-zA-Z0-9_-]", "_");
      } else {
        cleanBaseName = fileNameOnly.replaceAll("[^a-zA-Z0-9_-]", "_");
      }
    }

    String uniqueFilename = UUID.randomUUID() + "_" + cleanBaseName + extension;
    String folderSegment = "";

    if (folder != null && !folder.isBlank()) {
      String normalizedFolder = folder.trim().replace('\\', '/');
      while (normalizedFolder.startsWith("/")) {
        normalizedFolder = normalizedFolder.substring(1);
      }
      while (normalizedFolder.endsWith("/")) {
        normalizedFolder = normalizedFolder.substring(0, normalizedFolder.length() - 1);
      }
      if (!normalizedFolder.isEmpty()) {
        folderSegment = normalizedFolder + "/";
      }
    }

    String objectPath = folderSegment + uniqueFilename;
    log.info("Simulating OCI upload to bucket '{}' at path '{}'", bucketName, objectPath);

    return String.format(
        "https://objectstorage.%s.oraclecloud.com/n/%s/b/%s/o/%s",
        region, namespace != null ? namespace : "", bucketName, objectPath);
  }

  @Override
  public void deleteFile(String fileUrl) {
    if (fileUrl == null || fileUrl.isBlank()) {
      return;
    }
    log.info("Simulating OCI file deletion for URL: {}", fileUrl);
  }
}
