package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.response.vehicle.VehicleEvidenceContent;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface VehicleEvidenceStorage {
  String store(UUID aggregateId, String side, MultipartFile file);

  VehicleEvidenceContent load(String key);

  void delete(String key);
}
