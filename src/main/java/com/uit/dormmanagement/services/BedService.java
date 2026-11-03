package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.request.room.UpdateBedStatusRequest;
import com.uit.dormmanagement.dtos.response.room.BedResponse;
import java.util.UUID;

public interface BedService {
  BedResponse updateBedStatus(UUID bedId, UpdateBedStatusRequest request);
}
