package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.enums.VehicleType;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.VehicleErrorCode;
import com.uit.dormmanagement.services.VehicleRegistrationValidator;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class VehicleRegistrationValidatorImpl implements VehicleRegistrationValidator {

  private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB

  private static final Set<VehicleType> MOTORIZED_TYPES =
      EnumSet.of(
          VehicleType.SCOOTER, VehicleType.MANUAL_MOTORCYCLE, VehicleType.ELECTRIC_MOTORBIKE);

  @Override
  public ValidatedVehicleRegistration validate(
      VehicleType type,
      String plate,
      String brand,
      String color,
      MultipartFile front,
      MultipartFile back) {

    if (type == null) {
      throw new BaseAppException(VehicleErrorCode.INVALID_VEHICLE_TYPE_DATA);
    }

    if (brand == null || brand.isBlank() || color == null || color.isBlank()) {
      throw new BaseAppException(VehicleErrorCode.INVALID_VEHICLE_TYPE_DATA);
    }

    String trimmedBrand = brand.trim();
    String trimmedColor = color.trim();
    String canonicalPlate = null;

    boolean isMotorized = MOTORIZED_TYPES.contains(type);

    if (isMotorized) {
      if (plate == null || plate.isBlank()) {
        throw new BaseAppException(VehicleErrorCode.INVALID_VEHICLE_TYPE_DATA);
      }
      canonicalPlate = plate.trim().toUpperCase();

      if (front == null || front.isEmpty() || back == null || back.isEmpty()) {
        throw new BaseAppException(VehicleErrorCode.INVALID_VEHICLE_TYPE_DATA);
      }
      validateImageFile(front);
      validateImageFile(back);
    } else {
      if (plate != null && !plate.isBlank()) {
        canonicalPlate = plate.trim().toUpperCase();
      }
      if (front != null && !front.isEmpty()) {
        validateImageFile(front);
      }
      if (back != null && !back.isEmpty()) {
        validateImageFile(back);
      }
    }

    return new ValidatedVehicleRegistration(
        type, canonicalPlate, trimmedBrand, trimmedColor, front, back);
  }

  @Override
  public void validateImageFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BaseAppException(VehicleErrorCode.INVALID_EVIDENCE_FILE);
    }

    if (file.getSize() > MAX_FILE_SIZE_BYTES) {
      throw new BaseAppException(VehicleErrorCode.INVALID_EVIDENCE_FILE);
    }

    try (InputStream is = file.getInputStream()) {
      byte[] header = new byte[12];
      int read = is.readNBytes(header, 0, 12);
      if (read < 12) {
        throw new BaseAppException(VehicleErrorCode.INVALID_EVIDENCE_FILE);
      }

      if (isJpeg(header) || isPng(header) || isWebp(header)) {
        return; // Valid image
      }

      throw new BaseAppException(VehicleErrorCode.INVALID_EVIDENCE_FILE);
    } catch (IOException e) {
      throw new BaseAppException(VehicleErrorCode.INVALID_EVIDENCE_FILE);
    }
  }

  private boolean isJpeg(byte[] header) {
    return (header[0] == (byte) 0xFF) && (header[1] == (byte) 0xD8) && (header[2] == (byte) 0xFF);
  }

  private boolean isPng(byte[] header) {
    return (header[0] == (byte) 0x89)
        && (header[1] == (byte) 0x50)
        && (header[2] == (byte) 0x4E)
        && (header[3] == (byte) 0x47)
        && (header[4] == (byte) 0x0D)
        && (header[5] == (byte) 0x0A)
        && (header[6] == (byte) 0x1A)
        && (header[7] == (byte) 0x0A);
  }

  private boolean isWebp(byte[] header) {
    // RIFF (bytes 0-3) and WEBP (bytes 8-11)
    return (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F')
        && (header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P');
  }
}
