package com.uit.dormmanagement.services;

import com.uit.dormmanagement.dtos.request.auth.CreateTechnicianRequest;
import com.uit.dormmanagement.dtos.request.auth.UpdateTechnicianRequest;
import com.uit.dormmanagement.dtos.request.auth.UpdateTechnicianStatusRequest;
import com.uit.dormmanagement.dtos.response.auth.TechnicianResponse;
import com.uit.dormmanagement.dtos.response.auth.UserProfileResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

  UserProfileResponse getCurrentUserProfile(UUID userId);

  TechnicianResponse createTechnician(CreateTechnicianRequest request);

  Page<TechnicianResponse> getTechnicians(Pageable pageable, Boolean active, String search);

  TechnicianResponse getTechnicianById(UUID id);

  TechnicianResponse updateTechnicianStatus(UUID id, UpdateTechnicianStatusRequest request);

  TechnicianResponse updateTechnician(UUID id, UpdateTechnicianRequest request);
}
