package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.dtos.response.vehicle.VehicleEvidenceContent;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.services.impl.LocalVehicleEvidenceStorage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalVehicleEvidenceStorageTest {

  @TempDir Path tempDir;

  private LocalVehicleEvidenceStorage storage;

  @BeforeEach
  void setUp() {
    storage = new LocalVehicleEvidenceStorage(tempDir.toString());
  }

  @Test
  @DisplayName("store: saves file privately and returns valid relative key")
  void store_shouldSaveFileAndReturnKey() throws IOException {
    UUID vehicleId = UUID.randomUUID();
    byte[] content = "test-image-bytes".getBytes();
    MockMultipartFile file =
        new MockMultipartFile("front", "evidence.jpg", "image/jpeg", content);

    String key = storage.store(vehicleId, "front", file);

    assertThat(key).startsWith("vehicles/" + vehicleId + "/front-");
    assertThat(key).endsWith(".jpg");

    VehicleEvidenceContent loaded = storage.load(key);
    assertThat(loaded).isNotNull();
    assertThat(loaded.contentLength()).isEqualTo(content.length);
    assertThat(loaded.contentType()).isEqualTo("image/jpeg");
    assertThat(loaded.resource().getInputStream().readAllBytes()).isEqualTo(content);

    // Delete
    storage.delete(key);
    assertThatThrownBy(() -> storage.load(key))
        .isInstanceOf(BaseAppException.class)
        .matches(e -> ((BaseAppException) e).getErrorCode() == VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
  }

  @Test
  @DisplayName("load: rejects path traversal attempt")
  void load_whenPathTraversal_shouldThrowNotFound() {
    assertThatThrownBy(() -> storage.load("../../etc/passwd"))
        .isInstanceOf(BaseAppException.class)
        .matches(e -> ((BaseAppException) e).getErrorCode() == VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
  }

  @Test
  @DisplayName("load: non-existent file throws VEHICLE_EVIDENCE_NOT_FOUND")
  void load_whenNonExistent_shouldThrowNotFound() {
    assertThatThrownBy(() -> storage.load("vehicles/" + UUID.randomUUID() + "/front-dummy.jpg"))
        .isInstanceOf(BaseAppException.class)
        .matches(e -> ((BaseAppException) e).getErrorCode() == VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
  }
}
