package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.dtos.response.vehicle.VehicleEvidenceContent;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.services.VehicleEvidenceStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class LocalVehicleEvidenceStorage implements VehicleEvidenceStorage {

  private final Path baseDir;

  public LocalVehicleEvidenceStorage(
      @Value("${storage.vehicle.private-base-dir:private-storage/vehicles}") String privateBaseDir) {
    this.baseDir = Paths.get(privateBaseDir).toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.baseDir);
    } catch (IOException e) {
      log.error("Failed to create private vehicle storage directory: {}", this.baseDir, e);
    }
  }

  @Override
  public String store(UUID aggregateId, String side, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BaseAppException(VehicleErrorCode.INVALID_EVIDENCE_FILE);
    }

    String normalizedSide = "front".equalsIgnoreCase(side) ? "front" : "back";
    String extension = determineExtension(file);
    String relativeKey =
        "vehicles/" + aggregateId + "/" + normalizedSide + "-" + UUID.randomUUID() + extension;

    Path targetPath = resolveAndVerifyPath(relativeKey);

    try {
      Files.createDirectories(targetPath.getParent());
      try (InputStream is = file.getInputStream()) {
        Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
      }
      log.debug("Stored private vehicle evidence at {}", targetPath);
      return relativeKey;
    } catch (IOException e) {
      log.error("Failed to store private vehicle evidence", e);
      throw new BaseAppException(VehicleErrorCode.INVALID_EVIDENCE_FILE);
    }
  }

  @Override
  public VehicleEvidenceContent load(String key) {
    if (key == null || key.isBlank()) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
    }

    Path path = resolveAndVerifyPath(key);
    if (!Files.exists(path) || !Files.isRegularFile(path)) {
      throw new BaseAppException(VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
    }

    try {
      String contentType = Files.probeContentType(path);
      if (contentType == null) {
        if (key.endsWith(".png")) {
          contentType = "image/png";
        } else if (key.endsWith(".webp")) {
          contentType = "image/webp";
        } else {
          contentType = "image/jpeg";
        }
      }
      long size = Files.size(path);
      return new VehicleEvidenceContent(new FileSystemResource(path), contentType, size);
    } catch (IOException e) {
      log.error("Failed to read vehicle evidence file: {}", path, e);
      throw new BaseAppException(VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
    }
  }

  @Override
  public void delete(String key) {
    if (key == null || key.isBlank()) {
      return;
    }
    try {
      Path path = resolveAndVerifyPath(key);
      Files.deleteIfExists(path);
      log.debug("Deleted private vehicle evidence at {}", path);
    } catch (Exception e) {
      log.warn("Could not delete vehicle evidence with key: {}", key, e);
    }
  }

  private Path resolveAndVerifyPath(String key) {
    Path resolved = baseDir.resolve(key).normalize();
    if (!resolved.startsWith(baseDir)) {
      log.warn("Path traversal detected for key: {}", key);
      throw new BaseAppException(VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
    }
    return resolved;
  }

  private String determineExtension(MultipartFile file) {
    String original = file.getOriginalFilename();
    if (original != null && original.contains(".")) {
      String ext = original.substring(original.lastIndexOf('.')).toLowerCase();
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
}
