package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.response.vehicle.VehicleEvidenceContent;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.ObjectStorageNotFoundException;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.services.PrivateObjectStorage;
import com.uit.dormmanagement.services.StoredObjectContent;
import com.uit.dormmanagement.services.VehicleEvidenceStorage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class PrivateVehicleEvidenceStorage implements VehicleEvidenceStorage {

  private final PrivateObjectStorage privateObjectStorage;

  public PrivateVehicleEvidenceStorage(PrivateObjectStorage privateObjectStorage) {
    this.privateObjectStorage =
        Objects.requireNonNull(privateObjectStorage, "PrivateObjectStorage must not be null");
  }

  @Override
  public String store(UUID aggregateId, String side, MultipartFile file) {
    if (aggregateId == null || file == null || file.isEmpty()) {
      throw new BaseAppException(VehicleErrorCode.INVALID_EVIDENCE_FILE);
    }

    String normalizedSide = "front".equalsIgnoreCase(side) ? "front" : "back";
    String extension = determineExtension(file);
    String contentType = determineContentType(file, extension);
    String relativeKey =
        "vehicles/" + aggregateId + "/" + normalizedSide + "-" + UUID.randomUUID() + extension;

    try (InputStream is = file.getInputStream()) {
      privateObjectStorage.putObject(relativeKey, contentType, is, file.getSize());
      return relativeKey;
    } catch (IOException e) {
      log.warn("Failed to read vehicle evidence upload stream: {}", e.getClass().getSimpleName());
      throw new BaseAppException(VehicleErrorCode.INVALID_EVIDENCE_FILE);
    }
  }

  @Override
  public VehicleEvidenceContent load(String key) {
    if (key == null || key.isBlank()) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
    }

    StoredObjectContent stored;
    try {
      stored = privateObjectStorage.getObject(key);
    } catch (ObjectStorageNotFoundException | IllegalArgumentException e) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
    }

    String contentType = stored.contentType();
    if (contentType == null
        || contentType.isBlank()
        || MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(contentType)) {
      contentType = inferImageContentTypeFromKey(key);
    }
    return new VehicleEvidenceContent(stored.resource(), contentType, stored.contentLength());
  }

  @Override
  public void delete(String key) {
    if (key == null || key.isBlank()) {
      return;
    }
    try {
      privateObjectStorage.deleteObject(key);
    } catch (Exception e) {
      log.warn("Could not delete vehicle evidence object: {}", e.getClass().getSimpleName());
    }
  }

  private String determineExtension(MultipartFile file) {
    String original = file.getOriginalFilename();
    if (original != null && original.contains(".")) {
      String ext = original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT);
      if (ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png") || ext.equals(".webp")) {
        return ext;
      }
    }
    String contentType = file.getContentType();
    if ("image/png".equalsIgnoreCase(contentType)) {
      return ".png";
    }
    if ("image/webp".equalsIgnoreCase(contentType)) {
      return ".webp";
    }
    return ".jpg";
  }

  private String determineContentType(MultipartFile file, String extension) {
    String contentType = file.getContentType();
    if (MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(contentType)
        || MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType)
        || "image/webp".equalsIgnoreCase(contentType)) {
      return contentType.toLowerCase(Locale.ROOT);
    }
    return switch (extension) {
      case ".png" -> MediaType.IMAGE_PNG_VALUE;
      case ".webp" -> "image/webp";
      default -> MediaType.IMAGE_JPEG_VALUE;
    };
  }

  private String inferImageContentTypeFromKey(String key) {
    String lower = key.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".png")) {
      return MediaType.IMAGE_PNG_VALUE;
    }
    if (lower.endsWith(".webp")) {
      return "image/webp";
    }
    return MediaType.IMAGE_JPEG_VALUE;
  }
}
