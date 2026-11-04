package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.services.impl.LocalStorageServiceImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalStorageServiceTest {

  @TempDir Path tempDir;

  private LocalStorageServiceImpl storageService;

  @BeforeEach
  void setUp() {
    storageService = new LocalStorageServiceImpl(tempDir.toString());
  }

  @Test
  @DisplayName("uploadFile should save file to directory and return valid URL")
  void uploadFile_shouldSaveFileAndReturnUrl() {
    MockMultipartFile file =
        new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());

    String url = storageService.uploadFile(file, "contracts");

    assertThat(url).contains("/uploads/contracts/");
    assertThat(url).endsWith(".pdf");

    // Verify physical file was created on filesystem
    String relativePath = url.replaceFirst("^/uploads/", "");
    Path filePath = tempDir.resolve(relativePath);
    assertThat(Files.exists(filePath)).isTrue();
  }

  @Test
  @DisplayName("uploadFile with empty or null folder should save in root baseDir")
  void uploadFile_withNullOrEmptyFolder_shouldSaveInRootUploads() {
    MockMultipartFile file =
        new MockMultipartFile("file", "avatar.png", "image/png", "image data".getBytes());

    String url = storageService.uploadFile(file, null);

    assertThat(url).startsWith("/uploads/");
    assertThat(url).doesNotContain("/uploads//");
    assertThat(url).endsWith(".png");

    String relativePath = url.replaceFirst("^/uploads/", "");
    Path filePath = tempDir.resolve(relativePath);
    assertThat(Files.exists(filePath)).isTrue();
  }

  @Test
  @DisplayName("uploadFile should throw exception when file is empty")
  void uploadFile_shouldThrowExceptionWhenFileIsEmpty() {
    MockMultipartFile emptyFile =
        new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

    assertThatThrownBy(() -> storageService.uploadFile(emptyFile, "contracts"))
        .isInstanceOf(BaseAppException.class);
  }

  @Test
  @DisplayName("uploadFile should throw exception when file is null")
  void uploadFile_shouldThrowExceptionWhenFileIsNull() {
    assertThatThrownBy(() -> storageService.uploadFile(null, "contracts"))
        .isInstanceOf(BaseAppException.class);
  }

  @Test
  @DisplayName("uploadFile should reject path traversal in folder")
  void uploadFile_shouldRejectPathTraversalInFolder() {
    MockMultipartFile file =
        new MockMultipartFile("file", "hack.txt", "text/plain", "malicious".getBytes());

    assertThatThrownBy(() -> storageService.uploadFile(file, "../traversal"))
        .isInstanceOf(BaseAppException.class);
  }

  @Test
  @DisplayName("uploadFile should generate unique filenames for duplicate uploads")
  void uploadFile_shouldGenerateUniqueFilenameForDuplicateUploads() {
    MockMultipartFile file1 =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", "content 1".getBytes());
    MockMultipartFile file2 =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", "content 2".getBytes());

    String url1 = storageService.uploadFile(file1, "contracts");
    String url2 = storageService.uploadFile(file2, "contracts");

    assertThat(url1).isNotEqualTo(url2);

    Path path1 = tempDir.resolve(url1.replaceFirst("^/uploads/", ""));
    Path path2 = tempDir.resolve(url2.replaceFirst("^/uploads/", ""));

    assertThat(Files.exists(path1)).isTrue();
    assertThat(Files.exists(path2)).isTrue();
  }

  @Test
  @DisplayName("deleteFile should delete existing file")
  void deleteFile_shouldDeleteExistingFile() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "delete-me.pdf", "application/pdf", "to be deleted".getBytes());

    String url = storageService.uploadFile(file, "contracts");
    Path filePath = tempDir.resolve(url.replaceFirst("^/uploads/", ""));
    assertThat(Files.exists(filePath)).isTrue();

    storageService.deleteFile(url);

    assertThat(Files.exists(filePath)).isFalse();
  }

  @Test
  @DisplayName("deleteFile with full URL should extract relative path and delete")
  void deleteFile_withFullUrl_shouldExtractRelativePathAndDelete() {
    MockMultipartFile file =
        new MockMultipartFile("file", "full-url.pdf", "application/pdf", "delete full".getBytes());

    String url = storageService.uploadFile(file, "vehicles");
    String fullUrl = "http://localhost:8080" + url;
    Path filePath = tempDir.resolve(url.replaceFirst("^/uploads/", ""));
    assertThat(Files.exists(filePath)).isTrue();

    storageService.deleteFile(fullUrl);

    assertThat(Files.exists(filePath)).isFalse();
  }

  @Test
  @DisplayName("deleteFile should gracefully handle non-existent file")
  void deleteFile_shouldHandleNonExistentFileGracefully() {
    storageService.deleteFile("/uploads/contracts/non-existent.pdf");
  }

  @Test
  @DisplayName("deleteFile should do nothing for null or blank url")
  void deleteFile_withNullOrBlankUrl_shouldDoNothing() {
    storageService.deleteFile(null);
    storageService.deleteFile("");
    storageService.deleteFile("   ");
  }

  @Test
  @DisplayName("deleteFile should prevent path traversal attempts")
  void deleteFile_shouldPreventPathTraversal() throws IOException {
    Path outsideFile = Files.createTempFile("outside", ".txt");
    try {
      storageService.deleteFile("/uploads/../../" + outsideFile.getFileName());
      assertThat(Files.exists(outsideFile)).isTrue();
    } finally {
      Files.deleteIfExists(outsideFile);
    }
  }
}
