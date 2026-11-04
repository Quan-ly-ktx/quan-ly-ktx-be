package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.services.impl.OciObjectStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class OciObjectStorageServiceImplTest {

  private OciObjectStorageServiceImpl ociStorageService;

  @BeforeEach
  void setUp() {
    ociStorageService =
        new OciObjectStorageServiceImpl(
            "ocid1.tenancy.oc1..aaaa",
            "ocid1.user.oc1..bbbb",
            "20:3b:97:13:...",
            "/path/to/key.pem",
            "ap-singapore-1",
            "my-namespace",
            "dormitory-storage");
  }

  @Test
  @DisplayName("uploadFile should return valid OCI object storage URL")
  void uploadFile_shouldReturnValidOciUrl() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "contract.pdf", "application/pdf", "contract-data".getBytes());

    String url = ociStorageService.uploadFile(file, "contracts");

    assertThat(url)
        .startsWith(
            "https://objectstorage.ap-singapore-1.oraclecloud.com/n/my-namespace/b/dormitory-storage/o/contracts/");
    assertThat(url).endsWith(".pdf");
    assertThat(url).contains("_contract.pdf");
  }

  @Test
  @DisplayName("uploadFile with empty folder should return root object URL")
  void uploadFile_withEmptyFolder_shouldReturnRootUrl() {
    MockMultipartFile file =
        new MockMultipartFile("file", "car.jpg", "image/jpeg", "image-bytes".getBytes());

    String url = ociStorageService.uploadFile(file, null);

    assertThat(url)
        .startsWith(
            "https://objectstorage.ap-singapore-1.oraclecloud.com/n/my-namespace/b/dormitory-storage/o/");
    assertThat(url).doesNotContain("/o//");
    assertThat(url).endsWith(".jpg");
  }

  @Test
  @DisplayName("uploadFile should throw exception when file is empty")
  void uploadFile_shouldThrowExceptionWhenFileIsEmpty() {
    MockMultipartFile emptyFile =
        new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

    assertThatThrownBy(() -> ociStorageService.uploadFile(emptyFile, "contracts"))
        .isInstanceOf(BaseAppException.class);
  }

  @Test
  @DisplayName("uploadFile should throw exception when file is null")
  void uploadFile_shouldThrowExceptionWhenFileIsNull() {
    assertThatThrownBy(() -> ociStorageService.uploadFile(null, "contracts"))
        .isInstanceOf(BaseAppException.class);
  }

  @Test
  @DisplayName("deleteFile should complete without error")
  void deleteFile_shouldCompleteWithoutError() {
    ociStorageService.deleteFile(
        "https://objectstorage.ap-singapore-1.oraclecloud.com/n/my-namespace/b/dormitory-storage/o/contracts/test.pdf");
    ociStorageService.deleteFile(null);
    ociStorageService.deleteFile("");
  }
}
