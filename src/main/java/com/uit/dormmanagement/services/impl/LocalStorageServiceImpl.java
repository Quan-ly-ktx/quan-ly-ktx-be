package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.SystemErrorCode;
import com.uit.dormmanagement.exceptions.error.WebErrorCode;
import com.uit.dormmanagement.services.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class LocalStorageServiceImpl implements StorageService {

  private final String baseDir;
  private final Path rootPath;

  public LocalStorageServiceImpl() {
    this("uploads");
  }

  public LocalStorageServiceImpl(String baseDir) {
    this.baseDir = (baseDir == null || baseDir.isBlank()) ? "uploads" : baseDir;
    this.rootPath = Paths.get(this.baseDir).toAbsolutePath().normalize();
    initializeStorage();
  }

  private void initializeStorage() {
    try {
      if (!Files.exists(rootPath)) {
        Files.createDirectories(rootPath);
        log.info("Initialized local storage directory: {}", rootPath);
      }
    } catch (IOException e) {
      log.error("Could not initialize local storage directory: {}", rootPath, e);
      throw new BaseAppException(
          SystemErrorCode.INTERNAL_SERVER_ERROR, "Không thể khởi tạo thư mục lưu trữ cục bộ");
    }
  }

  @Override
  public String uploadFile(MultipartFile file, String folder) {
    if (file == null || file.isEmpty()) {
      throw new BaseAppException(WebErrorCode.BAD_REQUEST, "Tệp tải lên không được để trống");
    }

    validateFolder(folder);

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
      if (cleanBaseName.length() > 100) {
        cleanBaseName = cleanBaseName.substring(0, 100);
      }
    }

    String uniqueFilename = UUID.randomUUID() + "_" + cleanBaseName + extension;

    Path targetDir = rootPath;
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
        targetDir = rootPath.resolve(normalizedFolder).normalize();
        if (!targetDir.startsWith(rootPath)) {
          throw new BaseAppException(
              WebErrorCode.BAD_REQUEST, "Đường dẫn thư mục không hợp lệ: " + folder);
        }
        folderSegment = normalizedFolder + "/";
      }
    }

    try {
      if (!Files.exists(targetDir)) {
        Files.createDirectories(targetDir);
      }

      Path targetFilePath = targetDir.resolve(uniqueFilename).normalize();
      if (!targetFilePath.startsWith(rootPath)) {
        throw new BaseAppException(WebErrorCode.BAD_REQUEST, "Đường dẫn lưu tệp không hợp lệ");
      }

      try (InputStream inputStream = file.getInputStream()) {
        Files.copy(inputStream, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
      }

      log.info("Saved file locally at: {}", targetFilePath);
      return "/uploads/" + folderSegment + uniqueFilename;
    } catch (IOException e) {
      log.error("Failed to save file: {}", uniqueFilename, e);
      throw new BaseAppException(SystemErrorCode.INTERNAL_SERVER_ERROR, "Lỗi hệ thống khi lưu tệp");
    }
  }

  @Override
  public void deleteFile(String fileUrl) {
    if (fileUrl == null || fileUrl.isBlank()) {
      return;
    }

    String relativePath = extractRelativePath(fileUrl);
    if (relativePath.isBlank()) {
      return;
    }

    Path targetFilePath = rootPath.resolve(relativePath).normalize();

    // Prevent path traversal
    if (!targetFilePath.startsWith(rootPath)) {
      log.warn("Path traversal detected in deleteFile: {}", fileUrl);
      return;
    }

    // Ensure only regular files are deleted, never directories or root directory
    if (!Files.isRegularFile(targetFilePath)) {
      log.warn("Target is not a regular file for deletion: {}", targetFilePath);
      return;
    }

    try {
      boolean deleted = Files.deleteIfExists(targetFilePath);
      if (deleted) {
        log.info("Deleted local file: {}", targetFilePath);
      } else {
        log.debug("File does not exist for deletion: {}", targetFilePath);
      }
    } catch (IOException e) {
      log.error("Failed to delete file: {}", targetFilePath, e);
    }
  }

  private void validateFolder(String folder) {
    if (folder == null || folder.isBlank()) {
      return;
    }
    String trimmedFolder = folder.trim();
    if (trimmedFolder.contains("..")
        || trimmedFolder.startsWith("/")
        || trimmedFolder.startsWith("\\")) {
      throw new BaseAppException(
          WebErrorCode.BAD_REQUEST, "Đường dẫn thư mục không hợp lệ: " + folder);
    }
  }

  private String extractRelativePath(String fileUrl) {
    String cleanUrl = fileUrl.trim();
    int uploadsIndex = cleanUrl.indexOf("/uploads/");
    if (uploadsIndex >= 0) {
      return cleanUrl.substring(uploadsIndex + "/uploads/".length());
    }
    if (cleanUrl.startsWith("uploads/")) {
      return cleanUrl.substring("uploads/".length());
    }
    if (cleanUrl.startsWith("/")) {
      return cleanUrl.substring(1);
    }
    return cleanUrl;
  }
}
