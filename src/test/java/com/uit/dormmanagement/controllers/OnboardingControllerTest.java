package com.uit.dormmanagement.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uit.dormmanagement.dtos.request.onboarding.OnboardingRequest;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.contract.ContractDetailResponse;
import com.uit.dormmanagement.dtos.response.onboarding.GeneratedAccountInfo;
import com.uit.dormmanagement.dtos.response.onboarding.OnboardingResponse;
import com.uit.dormmanagement.dtos.response.tenant.TenantDetailResponse;
import com.uit.dormmanagement.enums.ContractStatus;
import com.uit.dormmanagement.enums.GenderPolicy;
import com.uit.dormmanagement.enums.ResidentStatus;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.exceptions.BaseAppException;
import com.uit.dormmanagement.exceptions.error.ContractErrorCode;
import com.uit.dormmanagement.exceptions.error.TenantErrorCode;
import com.uit.dormmanagement.services.OnboardingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OnboardingControllerTest {

  @Mock private OnboardingService onboardingService;

  @InjectMocks private OnboardingController onboardingController;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final ResponseFactory responseFactory = new ResponseFactory();

  private OnboardingRequest validRequest;
  private OnboardingResponse sampleResponse;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(onboardingController, "responseFactory", responseFactory);
    mockMvc =
        MockMvcBuilders.standaloneSetup(onboardingController)
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();

    UUID bedId = UUID.randomUUID();
    validRequest =
        OnboardingRequest.builder()
            .fullName("Nguyen Van A")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079200001234")
            .phone("0901234567")
            .email("nguyenvana@gmail.com")
            .permanentAddress("123 Le Loi, Q1, TP.HCM")
            .dateOfBirth(LocalDate.of(2002, 5, 15))
            .emergencyContactName("Nguyen Van B")
            .emergencyContactPhone("0909876543")
            .bedId(bedId)
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .depositAmount(new BigDecimal("1500000.00"))
            .build();

    TenantDetailResponse tenantDetail =
        TenantDetailResponse.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .tenantCode("NT-0001")
            .fullName("Nguyen Van A")
            .gender(GenderPolicy.MALE)
            .idCardNumber("079200001234")
            .phone("0901234567")
            .email("nguyenvana@gmail.com")
            .permanentAddress("123 Le Loi, Q1, TP.HCM")
            .dateOfBirth(LocalDate.of(2002, 5, 15))
            .emergencyContactName("Nguyen Van B")
            .emergencyContactPhone("0909876543")
            .residentStatus(ResidentStatus.ACTIVE)
            .currentBedId(bedId)
            .currentBedCode("B01")
            .currentRoomId(UUID.randomUUID())
            .currentRoomNumber("P.302")
            .vehicles(List.of())
            .contractHistory(List.of())
            .build();

    ContractDetailResponse contractDetail =
        ContractDetailResponse.builder()
            .id(UUID.randomUUID())
            .contractCode("HD-2026-0001")
            .tenantId(tenantDetail.id())
            .tenantName(tenantDetail.fullName())
            .tenantPhone(tenantDetail.phone())
            .bedId(bedId)
            .bedCode("B01")
            .roomId(tenantDetail.currentRoomId())
            .roomNumber("P.302")
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2027, 2, 28))
            .monthlyRent(new BigDecimal("1500000.00"))
            .depositAmount(new BigDecimal("1500000.00"))
            .status(ContractStatus.ACTIVE)
            .documents(List.of())
            .build();

    GeneratedAccountInfo accountInfo =
        GeneratedAccountInfo.builder()
            .username("nguyenvana@gmail.com")
            .email("nguyenvana@gmail.com")
            .phone("0901234567")
            .initialPassword("Password123@")
            .roomBed("P.302 - B01")
            .build();

    sampleResponse =
        OnboardingResponse.builder()
            .tenant(tenantDetail)
            .contract(contractDetail)
            .account(accountInfo)
            .build();
  }

  @Test
  @DisplayName("POST /api/v1/admin/onboarding should return HTTP 201 with OnboardingResponse")
  void onboardTenant_whenValidRequest_shouldReturn201Created() throws Exception {
    when(onboardingService.onboardTenant(any(OnboardingRequest.class))).thenReturn(sampleResponse);

    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.httpStatus").value(201))
        .andExpect(jsonPath("$.message").value("Tiếp nhận và xếp phòng thành công"))
        .andExpect(jsonPath("$.data.tenant.tenantCode").value("NT-0001"))
        .andExpect(jsonPath("$.data.tenant.fullName").value("Nguyen Van A"))
        .andExpect(jsonPath("$.data.contract.contractCode").value("HD-2026-0001"))
        .andExpect(jsonPath("$.data.account.username").value("nguyenvana@gmail.com"))
        .andExpect(jsonPath("$.data.account.initialPassword").value("Password123@"))
        .andExpect(jsonPath("$.data.account.roomBed").value("P.302 - B01"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/onboarding should return HTTP 400 when request validation fails")
  void onboardTenant_whenMissingRequiredFields_shouldReturn400BadRequest() throws Exception {
    OnboardingRequest invalidRequest =
        OnboardingRequest.builder()
            .fullName("") // Blank fullName
            .idCardNumber("123") // Invalid format (not 12 digits)
            .email("invalid-email")
            .phone("123") // Not 10 digits starting with 0
            .build();

    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("WEB_400_VAL"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/onboarding should return HTTP 400 when gender mismatch")
  void onboardTenant_whenGenderMismatch_shouldReturn400WithErrorCode() throws Exception {
    when(onboardingService.onboardTenant(any(OnboardingRequest.class)))
        .thenThrow(new BaseAppException(ContractErrorCode.GENDER_MISMATCH));

    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("CONTRACT_400_GENDER_MISMATCH"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/onboarding should return HTTP 400 when bed is not available")
  void onboardTenant_whenBedNotAvailable_shouldReturn400WithErrorCode() throws Exception {
    when(onboardingService.onboardTenant(any(OnboardingRequest.class)))
        .thenThrow(new BaseAppException(ContractErrorCode.BED_NOT_AVAILABLE));

    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.httpStatus").value(400))
        .andExpect(jsonPath("$.errorCode").value("CONTRACT_400_BED_UNAVAILABLE"));
  }

  @Test
  @DisplayName("POST /api/v1/admin/onboarding should return HTTP 409 when idCard is duplicated")
  void onboardTenant_whenDuplicateIdCard_shouldReturn409Conflict() throws Exception {
    when(onboardingService.onboardTenant(any(OnboardingRequest.class)))
        .thenThrow(new BaseAppException(TenantErrorCode.ID_CARD_NUMBER_DUPLICATED));

    mockMvc
        .perform(
            post("/api/v1/admin/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.httpStatus").value(409))
        .andExpect(jsonPath("$.errorCode").value("TENANT_409_ID_CARD"));
  }
}
