package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.services.impl.VehicleRegistrationValidatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockMultipartFile;

class VehicleRegistrationValidatorTest {

  private VehicleRegistrationValidator validator;

  private MockMultipartFile validJpeg;
  private MockMultipartFile validPng;
  private MockMultipartFile validWebp;
  private MockMultipartFile invalidFile;

  @BeforeEach
  void setUp() {
    validator = new VehicleRegistrationValidatorImpl();

    byte[] jpegBytes =
        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    validJpeg = new MockMultipartFile("front", "front.jpg", "image/jpeg", jpegBytes);

    byte[] pngBytes =
        new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    validPng = new MockMultipartFile("back", "back.png", "image/png", pngBytes);

    byte[] webpBytes = new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
    validWebp = new MockMultipartFile("front", "front.webp", "image/webp", webpBytes);

    invalidFile =
        new MockMultipartFile(
            "file", "text.txt", "text/plain", "This is plain text and not an image".getBytes());
  }

  @ParameterizedTest
  @EnumSource(
      value = VehicleType.class,
      names = {"SCOOTER", "MANUAL_MOTORCYCLE", "ELECTRIC_MOTORBIKE"})
  @DisplayName("Motorized types require nonblank plate and both front and back evidence")
  void validate_motorizedTypes_shouldRequirePlateAndBothImages(VehicleType type) {
    // Missing plate
    assertThatThrownBy(() -> validator.validate(type, "", "Honda", "Red", validJpeg, validPng))
        .isInstanceOf(BaseAppException.class)
        .matches(
            e ->
                ((BaseAppException) e).getErrorCode()
                    == VehicleErrorCode.INVALID_VEHICLE_TYPE_DATA);

    // Missing back image
    assertThatThrownBy(
            () -> validator.validate(type, "59-A1 12345", "Honda", "Red", validJpeg, null))
        .isInstanceOf(BaseAppException.class)
        .matches(
            e ->
                ((BaseAppException) e).getErrorCode()
                    == VehicleErrorCode.INVALID_VEHICLE_TYPE_DATA);

    // Valid motorized registration
    VehicleRegistrationValidator.ValidatedVehicleRegistration validated =
        validator.validate(type, " 59-a1 12345 ", " Honda ", " Red ", validJpeg, validPng);

    assertThat(validated.vehicleType()).isEqualTo(type);
    assertThat(validated.licensePlate()).isEqualTo("59-A1 12345");
    assertThat(validated.brand()).isEqualTo("Honda");
    assertThat(validated.color()).isEqualTo("Red");
    assertThat(validated.frontEvidence()).isEqualTo(validJpeg);
    assertThat(validated.backEvidence()).isEqualTo(validPng);
  }

  @ParameterizedTest
  @EnumSource(
      value = VehicleType.class,
      names = {"BICYCLE", "ELECTRIC_BICYCLE"})
  @DisplayName("Bicycle types permit null plate and null images")
  void validate_bicycleTypes_shouldPermitNullPlateAndImages(VehicleType type) {
    VehicleRegistrationValidator.ValidatedVehicleRegistration validated =
        validator.validate(type, null, "Giant", "Blue", null, null);

    assertThat(validated.vehicleType()).isEqualTo(type);
    assertThat(validated.licensePlate()).isNull();
    assertThat(validated.brand()).isEqualTo("Giant");
    assertThat(validated.color()).isEqualTo("Blue");
    assertThat(validated.frontEvidence()).isNull();
    assertThat(validated.backEvidence()).isNull();
  }

  @Test
  @DisplayName("Image validation rejects invalid magic bytes or empty files")
  void validateImageFile_shouldRejectInvalidMagicBytes() {
    assertThatThrownBy(() -> validator.validateImageFile(invalidFile))
        .isInstanceOf(BaseAppException.class)
        .matches(
            e -> ((BaseAppException) e).getErrorCode() == VehicleErrorCode.INVALID_EVIDENCE_FILE);

    MockMultipartFile emptyFile =
        new MockMultipartFile("empty", "empty.jpg", "image/jpeg", new byte[0]);
    assertThatThrownBy(() -> validator.validateImageFile(emptyFile))
        .isInstanceOf(BaseAppException.class)
        .matches(
            e -> ((BaseAppException) e).getErrorCode() == VehicleErrorCode.INVALID_EVIDENCE_FILE);
  }
}
