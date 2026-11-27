package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.dtos.response.vehicle.VehicleEvidenceContent;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.services.impl.PrivateVehicleEvidenceStorage;
import com.uit.dormmanagement.support.InMemoryPrivateObjectStorage;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PrivateVehicleEvidenceStorageTest {

  private InMemoryPrivateObjectStorage objectStorage;
  private PrivateVehicleEvidenceStorage evidenceStorage;

  @BeforeEach
  void setUp() {
    objectStorage = new InMemoryPrivateObjectStorage();
    evidenceStorage = new PrivateVehicleEvidenceStorage(objectStorage);
  }

  @Test
  @DisplayName(
      "store saves object under vehicles/{id}/{side}-... and load/delete round-trip cleanly")
  void storeLoadDelete_shouldRoundTrip() throws IOException {
    UUID vehicleId = UUID.randomUUID();
    byte[] content = "test-image-bytes".getBytes();
    MockMultipartFile file = new MockMultipartFile("front", "evidence.jpg", "image/jpeg", content);

    String key = evidenceStorage.store(vehicleId, "front", file);

    assertThat(key).startsWith("vehicles/" + vehicleId + "/front-");
    assertThat(key).endsWith(".jpg");
    assertThat(objectStorage.exists(key)).isTrue();

    VehicleEvidenceContent loaded = evidenceStorage.load(key);
    assertThat(loaded.contentLength()).isEqualTo(content.length);
    assertThat(loaded.contentType()).isEqualTo("image/jpeg");
    assertThat(loaded.resource().getInputStream().readAllBytes()).isEqualTo(content);

    evidenceStorage.delete(key);
    assertThat(objectStorage.exists(key)).isFalse();
    assertThatThrownBy(() -> evidenceStorage.load(key))
        .isInstanceOf(BaseAppException.class)
        .matches(
            e ->
                ((BaseAppException) e).getErrorCode()
                    == VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
  }

  @Test
  @DisplayName("store resolves extension and content type for PNG and WebP files")
  void store_shouldPreservePngAndWebpTypes() {
    UUID vehicleId = UUID.randomUUID();
    MockMultipartFile pngFile =
        new MockMultipartFile("back", "card.png", "image/png", new byte[] {1, 2, 3});
    MockMultipartFile webpFile =
        new MockMultipartFile("front", "card", "image/webp", new byte[] {4, 5, 6});

    String pngKey = evidenceStorage.store(vehicleId, "back", pngFile);
    String webpKey = evidenceStorage.store(vehicleId, "front", webpFile);

    assertThat(pngKey).startsWith("vehicles/" + vehicleId + "/back-").endsWith(".png");
    assertThat(evidenceStorage.load(pngKey).contentType()).isEqualTo("image/png");
    assertThat(webpKey).startsWith("vehicles/" + vehicleId + "/front-").endsWith(".webp");
    assertThat(evidenceStorage.load(webpKey).contentType()).isEqualTo("image/webp");
  }

  @Test
  @DisplayName("load rejects traversal keys and missing keys with VEHICLE_EVIDENCE_NOT_FOUND")
  void load_whenInvalidOrMissing_shouldThrowNotFound() {
    assertThatThrownBy(() -> evidenceStorage.load("../../etc/passwd"))
        .isInstanceOf(BaseAppException.class)
        .matches(
            e ->
                ((BaseAppException) e).getErrorCode()
                    == VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);

    assertThatThrownBy(
            () -> evidenceStorage.load("vehicles/" + UUID.randomUUID() + "/front-missing.jpg"))
        .isInstanceOf(BaseAppException.class)
        .matches(
            e ->
                ((BaseAppException) e).getErrorCode()
                    == VehicleErrorCode.VEHICLE_EVIDENCE_NOT_FOUND);
  }
}
