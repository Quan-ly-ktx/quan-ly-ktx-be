package com.uit.dormmanagement.services;

import com.uit.dormmanagement.enums.VehicleType;
import org.springframework.web.multipart.MultipartFile;

public interface VehicleRegistrationValidator {

  record ValidatedVehicleRegistration(
      VehicleType vehicleType,
      String licensePlate,
      String brand,
      String color,
      MultipartFile frontEvidence,
      MultipartFile backEvidence) {}

  ValidatedVehicleRegistration validate(
      VehicleType type,
      String plate,
      String brand,
      String color,
      MultipartFile front,
      MultipartFile back);

  void validateImageFile(MultipartFile file);
}
